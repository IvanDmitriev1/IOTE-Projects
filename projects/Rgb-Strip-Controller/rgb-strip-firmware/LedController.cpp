#include "LedController.h"

namespace
{
  constexpr uint8_t LedPin = 5;
  constexpr uint8_t LedCount = 16;
  constexpr uint32_t FrameIntervalMs = 30;
} // namespace

LedController::LedController() : m_strip(LedCount, LedPin, NEO_GRB + NEO_KHZ800)
{
}

void LedController::Begin()
{
    m_strip.begin();
    m_strip.clear();
    m_strip.show();
}

void LedController::Update()
{
    const uint32_t now = millis();
    if (!m_rainbowActive || uint32_t(now - m_lastFrameMs) < FrameIntervalMs)
    {
        return;
    }

    m_lastFrameMs = now;
    m_phase = uint8_t(m_phase + (m_direction == Direction::Forward ? 1 : -1));

    for (uint8_t i = 0; i < LedCount; ++i)
    {
        const uint16_t hue = uint16_t(uint16_t(m_phase) * 256U + uint32_t(i) * 65536UL / LedCount);
        m_strip.setPixelColor(i, m_strip.ColorHSV(hue));
    }
    m_strip.show();
}

void LedController::SetRgb(uint8_t red, uint8_t green, uint8_t blue)
{
    m_rainbowActive = false;
    m_strip.fill(m_strip.Color(red, green, blue));
    m_strip.show();
}

void LedController::StartRainbow(Direction direction)
{
    m_direction = direction;
    if (!m_rainbowActive)
    {
        m_rainbowActive = true;
        m_lastFrameMs = millis() - FrameIntervalMs;
    }
}