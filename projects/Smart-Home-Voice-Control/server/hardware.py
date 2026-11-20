from collections.abc import Callable, Iterator
from contextlib import ExitStack, contextmanager
from dataclasses import dataclass
from time import sleep

from operations import RGB


@dataclass(frozen=True)
class Hardware:
    temperature: Callable[[], float]
    pressure: Callable[[], float]
    humidity: Callable[[], float] | None
    set_color: Callable[[RGB], None]


@contextmanager
def open_hardware() -> Iterator[Hardware]:
    import board
    import neopixel
    from adafruit_bme280.basic import Adafruit_BME280_I2C
    from adafruit_bmp280 import Adafruit_BMP280_I2C
    from adafruit_raspberry_pi5_neopixel_write import free_pio

    with ExitStack() as pio, ExitStack() as resources:
        pixels = neopixel.NeoPixel(
            board.D18, 12, brightness=0.1, auto_write=False, pixel_order=neopixel.GRB
        )

        def set_color(color: RGB) -> None:
            pixels.fill(color)
            pixels.show()

        def release_pio() -> None:
            # Let the PIO finish transmitting before releasing it.
            sleep(0.001)
            free_pio()

        resources.callback(pixels.deinit)
        set_color((0, 0, 0))
        pio.callback(release_pio)

        i2c = board.I2C()
        resources.callback(i2c.deinit)
        if not i2c.try_lock():
            raise RuntimeError("Could not lock I2C1 for sensor detection")
        try:
            chip = bytearray(1)
            for address in (0x76, 0x77):
                try:
                    i2c.writeto_then_readfrom(address, b"\xd0", chip)
                except OSError:
                    continue
                if chip[0] in (0x60, 0x58):
                    break
            else:
                raise RuntimeError("No BME280 or BMP280 found at 0x76 or 0x77")
        finally:
            i2c.unlock()

        humidity = None
        if chip[0] == 0x60:
            sensor = Adafruit_BME280_I2C(i2c, address=address)
            humidity = lambda: sensor.humidity
        else:
            sensor = Adafruit_BMP280_I2C(i2c, address=address)

        def pressure() -> float:
            try:
                return sensor.pressure
            except ArithmeticError as error:
                raise RuntimeError("Pressure reading failed") from error

        yield Hardware(lambda: sensor.temperature, pressure, humidity, set_color)
