#include <Arduino.h>
#include <DHT.h>
#include <PubSubClient.h>
#include <SoftwareSerial.h>
#include <WiFiEsp.h>
#include <math.h>
#include "Bme280Sensor.h"
#include "WiFiController.h"

char ssid[] = "aiote";
char password[] = "12345678";

SoftwareSerial espSerial(6, 7); // RX, TX
WiFiController wifi(ssid, password);
WiFiEspClient client;
PubSubClient mqttClient(client);
IPAddress mqttServer(10,99,245,104);
const char topic[] = "channel1";
const char mqttClientId[] = "remote-weather-station";

DHT dht(5, DHT11);
Bme280Sensor bme;
constexpr uint32_t SampleIntervalMs = 30000;
constexpr uint32_t MqttReconnectIntervalMs = 5000;
uint32_t lastSampleMs = 0;
uint32_t lastMqttAttemptMs = 0;

void PublishTelemetry(bool online)
{
    const float humidity = dht.readHumidity();
    const float temperature = dht.readTemperature();
    const float pressure = bme.Read();
    const int light = analogRead(A0) * 0.9765625;

    Serial.print(F("DHT11 temperature (C): "));
    Serial.println(temperature, 0);
    Serial.print(F("DHT11 humidity (%): "));
    Serial.println(humidity, 0);
    Serial.print(F("Light (sensor units): "));
    Serial.println(light);
    bme.Report();

    if (!isfinite(temperature) || !isfinite(humidity) || !isfinite(pressure) ||
        humidity < 0 || humidity > 100 || pressure <= 0)
    {
        Serial.println(F("Invalid sensor reading; skipping MQTT sample."));
        return;
    }
    if (!online || !mqttClient.connected())
    {
        Serial.println(F("MQTT offline; sample reported locally only."));
        return;
    }

    const String payload = String(F("{\"t\":\"")) + String(temperature, 0) +
        F("\",\"h\":\"") + String(humidity, 0) + F("\",\"p\":\"") +
        String(pressure, 0) + F("\",\"l\":\"") + String(light) + F("\"}");

    digitalWrite(LED_BUILTIN, HIGH);
    if (mqttClient.publish(topic, payload.c_str()))
    {
        Serial.print(F("Published: "));
        Serial.println(payload);
    }
    else
    {
        Serial.println(F("MQTT publish failed."));
    }
    digitalWrite(LED_BUILTIN, LOW);
}

void setup()
{
    Serial.begin(9600);
    Serial.println(F("Remote weather station starting."));
    espSerial.begin(9600);
    pinMode(LED_BUILTIN, OUTPUT);
    digitalWrite(LED_BUILTIN, LOW);
    Serial.println(F("Initializing DHT11..."));
    dht.begin();
    bme.Begin();
    mqttClient.setServer(mqttServer, 1883);
    Serial.println(F("Initializing Wi-Fi..."));
    wifi.Begin(espSerial);
    lastMqttAttemptMs = millis() - MqttReconnectIntervalMs;
    lastSampleMs = millis() - SampleIntervalMs;
}

void loop()
{
    const bool online = wifi.Update();
    if (online)
    {
        if (!mqttClient.connected() &&
            uint32_t(millis() - lastMqttAttemptMs) >= MqttReconnectIntervalMs)
        {
            lastMqttAttemptMs = millis();
            Serial.println(F("Connecting to MQTT server..."));
            if (mqttClient.connect(mqttClientId))
            {
                Serial.println(F("MQTT connected."));
            }
            else
            {
                Serial.print(F("MQTT connection failed; state: "));
                Serial.println(mqttClient.state());
            }
        }
        if (mqttClient.connected())
        {
            mqttClient.loop();
        }
    }

    const uint32_t now = millis();
    if (uint32_t(now - lastSampleMs) >= SampleIntervalMs)
    {
        lastSampleMs = now;
        PublishTelemetry(online);
    }
}
