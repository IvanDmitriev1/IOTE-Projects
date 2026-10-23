#pragma once

#include <Arduino.h>

class WiFiController
{
  public:
    WiFiController(char *ssid, char *password);
    void Begin(Stream &esp);
    bool Update();

  private:
    void Connect();

    char* m_ssid;
    char* m_password;
    bool m_online = false;
    uint32_t m_lastConnectAttemptMs = 0;
    uint32_t m_lastWiFiCheckMs = 0;
};
