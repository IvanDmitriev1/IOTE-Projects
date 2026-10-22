#pragma once

#include <Adafruit_NeoPixel.h>
#include <Arduino.h>

class LedController
{
  public:
    enum class Direction : uint8_t
    {
        Forward = 1,
        Reverse = 2
    };

  public:
    LedController();

  public:
    void Begin();
    void Update();

    void SetRgb(uint8_t red, uint8_t green, uint8_t blue);
    void StartRainbow(Direction direction);

  private:
    Adafruit_NeoPixel m_strip;
    bool m_rainbowActive = false;
    Direction m_direction = Direction::Forward;
    uint8_t m_phase = 0;
    uint32_t m_lastFrameMs = 0;
};
