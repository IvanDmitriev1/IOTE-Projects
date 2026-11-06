#include "Bme280Sensor.h"
#include <Wire.h>

void Bme280Sensor::Begin()
{
    Serial.println(F("Initializing I2C: SDA=A4, SCL=A5..."));
    Wire.begin();
    Wire.setWireTimeout(25000, true);
    Serial.println(F("Searching for BME280/BMP280 at I2C address 0x76..."));
    while (!m_sensor.begin())
    {
        Serial.println(F("Could not find BME280/BMP280 sensor!"));
        delay(1000);
    }
    switch (m_sensor.chipModel())
    {
        case BME280::ChipModel_BME280:
            Serial.println(F("Found BME280 sensor! Success."));
            break;
        case BME280::ChipModel_BMP280:
            Serial.println(F("Found BMP280 sensor! No humidity available; using DHT11."));
            break;
        default:
            Serial.println(F("Found UNKNOWN sensor! Error!"));
            break;
    }
}

float Bme280Sensor::Read()
{
    m_sensor.read(m_pressure, m_temperature, m_humidity,
        BME280::TempUnit_Celsius, BME280::PresUnit_torr);
    return m_pressure;
}

void Bme280Sensor::Report()
{
    Serial.print(F("BME/BMP280 temperature (C): "));
    Serial.println(m_temperature);
    if (m_sensor.chipModel() == BME280::ChipModel_BME280)
    {
        Serial.print(F("BME280 humidity (%): "));
        Serial.println(m_humidity);
    }
    Serial.print(F("BME/BMP280 pressure (mmHg): "));
    Serial.println(m_pressure, 0);
}
