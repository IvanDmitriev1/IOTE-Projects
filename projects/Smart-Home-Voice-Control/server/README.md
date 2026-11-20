# Smart Home Voice Control server

Run the server on a Raspberry Pi 5 with Raspberry Pi OS and Python. Connect one BME280 or BMP280 and a 12-pixel GRB NeoPixel strip.

## Wiring

Power down the Pi and strip supply before connecting them.

| Connection | Raspberry Pi 5 header |
| --- | --- |
| Sensor 3.3 V supply | 3.3 V, physical pin 1 |
| Sensor SDA | GPIO2 / I2C1 SDA, physical pin 3 |
| Sensor SCL | GPIO3 / I2C1 SCL, physical pin 5 |
| Common ground | GND, physical pin 6 |
| Strip data through level shifter | GPIO10 / SPI0 MOSI, physical pin 19 |

Set the sensor breakout for I2C at address `0x76` or `0x77`. The server reads the chip ID at startup to select the driver.

## Setup and run

```sh
sudo apt update
sudo apt install python3-venv python3-lgpio python3-libgpiod i2c-tools
sudo raspi-config nonint do_i2c 0
sudo raspi-config nonint do_spi 0
```

The sensor uses I2C and the strip uses SPI on GPIO10. Leave `dtparam=audio` as it is.

### 2. Create the virtual environment and install dependencies

```sh
python3 -m venv --system-site-packages .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
```

### 3. Start the server

Replace `192.168.1.50` with your Pi’s private LAN address.

```sh
python -m uvicorn main:app --host 192.168.1.50 --port 8000 --workers 1
```

## GET endpoints

| Path | Plain-text response |
| --- | --- |
| `/temperature` | Rounded degrees Celsius |
| `/pressure` | Rounded mmHg |
| `/humidity` | Rounded relative humidity percentage, or HTTP 501 on BMP280 |
| `/weather` | Forecast based on measured pressure |
| `/FFFFFF`, `/FF0000`, `/00FF00`, `/0000FF`, `/FFFF00`, `/FF00FF`, `/000000`, `/random` | The requested path after setting the strip |

For a quick check, request `http://192.168.1.50:8000/temperature` using the Pi's actual address.
