# Remote weather station firmware

This Arduino Uno sketch reads DHT11 temperature/humidity, BME280 or BMP280 pressure, and analog light. It reports all sensor readings over USB serial every 30 seconds, including when Wi-Fi or MQTT is unavailable. BME280 temperature and humidity are also reported; BMP280 has no humidity measurement. When MQTT is connected, the sample is published to topic `channel1`. The copied Wi-Fi controller handles the access point connection and reconnection.

## Requirements

- Arduino Uno (or a compatible ATmega328P board)
- The [WiFiEsp library](https://github.com/bportaluri/WiFiEsp)
- [DHT sensor library by Adafruit](https://github.com/adafruit/DHT-sensor-library) and its Adafruit Unified Sensor dependency
- [BME280 by Tyler Glenn](https://github.com/finitespace/BME280), which provides `BME280I2C.h`
- [PubSubClient by Nick O'Leary](https://github.com/knolleary/pubsubclient)
- A Wi-Fi module with AT firmware whose UART is configured to **9600 baud**

Install the listed libraries in Arduino IDE Library Manager. Edit `ssid`, `password`, and the example `mqttServer` IP (`192.168.1.106`) near the top of `remote-weather-station-firmware.ino`. The broker uses port `1883` without authentication, matching the supplied local-network example. Give `mqttClientId` a unique value if another station connects to the same broker.

Open the `remote-weather-station-firmware` folder in Arduino IDE, select **Arduino Uno**, and upload the sketch. Open Serial Monitor at **9600 baud** to see startup, connection, IP address, readings, and publish results. Wi-Fi status is checked every 30 seconds, Wi-Fi retries occur every 10 seconds, and MQTT retries every 5 seconds. Sampling uses `millis()` so MQTT keepalive is serviced between samples.

To observe reconnection, turn the access point off and wait for `Wi-Fi disconnected.` in Serial Monitor, then turn it back on. The next retry should print a new connection message and IP address.

## Wiring

- Module TX to Uno D6 (SoftwareSerial RX)
- Module RX to Uno D7 (SoftwareSerial TX)
- Module ground to Uno GND
- Supply power and logic levels required by the specific module manufacturer
- DHT11 data to Uno D5; use the sensor/module's required pull-up resistor
- Analog light sensor output to Uno A0
- BME280/BMP280 SDA to Uno A4 and SCL to Uno A5 (default I2C address `0x76`)
- Sensor grounds to Uno GND; use each breakout's specified power supply and I2C logic levels

Uno D0 and D1 remain available for the USB serial connection and upload.

## Readings and pressure units

Example payload: `{"t":"26","h":"16","p":"766","l":"267"}`. Temperature is Celsius, humidity is percent RH, and pressure is mmHg-equivalent. Values are formatted without decimal places. Light preserves the supplied example's scaling (`analogRead(A0) * 0.9765625`); it is an uncalibrated sensor signal, not lux.

`Bme280Sensor` owns sensor initialization (`Begin()`), reading (`Read()`), and Serial reporting (`Report()`). It reads pressure, temperature, and humidity together with `TempUnit_Celsius` and `PresUnit_torr`. The library performs the pressure unit conversion internally; the sketch has no pressure conversion formula. MQTT fields `t` and `h` still use DHT11, as in the supplied example; `p` uses BME280/BMP280 pressure. Select **mmHg** as the MQTT source pressure unit in the Android app; its display remains in hPa.

Setup reports the detected BME280/BMP280 model. If the pressure sensor cannot initialize, setup waits and logs the failure. Invalid readings are printed for diagnosis but skip the MQTT sample. Offline samples are reported locally, and failed publications are logged. There is no offline sample queue, HTTP server, or ThingSpeak upload.

Startup logs identify DHT11, I2C, pressure sensor, and Wi-Fi initialization separately. Wi-Fi starts only after the pressure sensor initializes. I2C transactions have a 25 ms timeout and reset the Uno's I2C peripheral on timeout, so a stuck bus can return an error instead of hanging silently. Repeated pressure sensor errors require checking its power, ground, SDA/SCL wiring, and I2C address.
