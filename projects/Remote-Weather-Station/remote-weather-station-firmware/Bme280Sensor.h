#pragma once

#include <BME280I2C.h>
#include <math.h>

class Bme280Sensor
{
  public:
    void Begin();
    float Read();
    void Report();

  private:
    BME280I2C m_sensor;
    float m_pressure = NAN;
    float m_temperature = NAN;
    float m_humidity = NAN;
};
