#include "WiFiController.h"
#include <WiFiEsp.h>

namespace
{
    constexpr uint32_t ReconnectIntervalMs = 10000;
    constexpr uint32_t WiFiCheckIntervalMs = 30000;
} // namespace

WiFiController::WiFiController(char *ssid, char *password) : m_ssid(ssid), m_password(password)
{
}

void WiFiController::Begin(Stream &esp)
{
    WiFi.init(&esp);
    Connect();
}

bool WiFiController::Update()
{
    const uint32_t now = millis();

    if (!m_online)
    {
        if (uint32_t(now - m_lastConnectAttemptMs) >= ReconnectIntervalMs)
        {
            Connect();
        }
        return m_online;
    }

    if (uint32_t(now - m_lastWiFiCheckMs) >= WiFiCheckIntervalMs)
    {
        m_lastWiFiCheckMs = now;
        m_online = WiFi.status() == WL_CONNECTED;
        if (!m_online)
        {
            Serial.println(F("Wi-Fi disconnected."));
        }
    }

    return m_online;
}

void WiFiController::Connect()
{
    m_lastConnectAttemptMs = millis();
    Serial.println(F("Connecting to Wi-Fi..."));
    m_online = WiFi.begin(m_ssid, m_password) == WL_CONNECTED;
    if (m_online)
    {
        m_lastWiFiCheckMs = millis();
        Serial.print(F("Wi-Fi connected. IP address: "));
        Serial.println(WiFi.localIP());
    }
    else
    {
        Serial.println(F("Wi-Fi connection failed; retrying in 10 seconds."));
    }
}
