#include "HttpHelpers.h"
#include "LedController.h"
#include "WiFiController.h"
#include <SoftwareSerial.h>
#include <WiFiEsp.h>

char ssid[] = "aiote";
char password[] = "12345678";

LedController leds;
SoftwareSerial espSerial(6, 7); // RX, TX
WiFiController wifi(ssid, password);
WiFiEspServer server(80);
Application app;
char transferEncoding[24] = {};
bool listening = false;

void HandleRgb(Request &req, Response &res)
{
    uint8_t red{};
    uint8_t green{};
    uint8_t blue{};

    if (!HasNoRequestBody(req, transferEncoding) || !AreRgbKeysValid(req.query()) || !QueryByte(req, "r", red) ||
        !QueryByte(req, "g", green) || !QueryByte(req, "b", blue))
    {
        res.sendStatus(400);
        return;
    }

    leds.SetRgb(red, green, blue);
    res.set("Cache-Control", "no-store");
    res.sendStatus(204);
}

void HandleRainbow(Request &req, Response &res)
{
    if (!HasNoRequestBody(req, transferEncoding))
    {
        res.sendStatus(400);
        return;
    }

    LedController::Direction direction = LedController::Direction::Forward;
    const char *query = req.query();

    if (query && *query)
    {
        if (strcmp(query, "direction=2") == 0)
        {
            direction = LedController::Direction::Reverse;
        }
        else if (strcmp(query, "direction=1") != 0)
        {
            res.sendStatus(400);
            return;
        }
    }

    leds.StartRainbow(direction);
    res.set("Cache-Control", "no-store");
    res.sendStatus(204);
}

void HandleFallback(Request &req, Response &res)
{
    if (res.statusSent())
    {
        return;
    }

    if (req.method() != Request::GET)
    {
        res.set("Allow", "GET");
        res.sendStatus(405);
    }
    else
    {
        res.sendStatus(404);
    }
}

void UpdateHttp(bool online)
{
    if (!online)
    {
        listening = false;
        return;
    }

    if (!listening)
    {
        server.begin();
        listening = true;
    }

    WiFiEspClient client = server.available();
    if (!client)
    {
        return;
    }

    app.process(&client);
    client.stop();
}

void setup()
{
    Serial.begin(9600);
    Serial.println(F("RGB strip controller starting. Initializing Wi-Fi..."));
    espSerial.begin(9600);
    leds.Begin();
    app.get("/rgb", &HandleRgb);
    app.get("/rainbow", &HandleRainbow);
    app.use(&HandleFallback);
    app.header("Transfer-Encoding", transferEncoding, sizeof(transferEncoding));
    app.setTimeout(3000); // aWOT input inactivity timeout, in milliseconds
    wifi.Begin(espSerial);
}

void loop()
{
    UpdateHttp(wifi.Update());
    leds.Update();
}
