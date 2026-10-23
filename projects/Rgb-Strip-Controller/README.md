# RGB Strip Controller

This project contains the Android controller app and Arduino firmware for an RGB LED strip.

## Components

- Desktop app — Windows and Linux x64/ARM64 desktop controller.
- [Android app](android/README.md) — Android Studio setup, build, lint, and app behavior.
- [Firmware sketch](rgb-strip-firmware/rgb-strip-firmware.ino) — Arduino firmware source.

The app communicates with the firmware over the local network using the documented `/rgb` and `/rainbow` HTTP endpoints. Configure the firmware network credentials for your environment before uploading it.

## Desktop app

The desktop app sends no network request on startup. Enter the firmware's address, then use **Apply** or **Start Rainbow**. Turning power off and pressing **Apply** sends zero RGB values while keeping the chosen color in the app.

```powershell
dotnet build RgbStripController.slnx -c Release
dotnet publish RgbStripController.Desktop/RgbStripController.Desktop.csproj -c Release -r win-x64 --self-contained true -p:PublishTrimmed=false -p:PublishAot=false -o publish/win-x64
dotnet publish RgbStripController.Desktop/RgbStripController.Desktop.csproj -c Release -r linux-x64 --self-contained true -p:PublishTrimmed=false -p:PublishAot=false -o publish/linux-x64
dotnet publish RgbStripController.Desktop/RgbStripController.Desktop.csproj -c Release -r linux-arm64 --self-contained true -p:PublishTrimmed=false -p:PublishAot=false -o publish/linux-arm64
```

## Firmware setup

1. Install the `Adafruit NeoPixel`, `WiFiEsp`, and `aWOT` Arduino libraries.
2. Edit the sketch's `ssid` and `password` values for the Wi-Fi network the controller should join.
3. Connect Troyka Wi-Fi TX to Uno D8 (software RX), Troyka RX to Uno D9 (software TX), and Troyka G to Uno GND. Move the Wi-Fi serial wires off D0/D1, which are reserved for USB Serial Monitor. Keep LED data on D5.
4. Power the Troyka module through its regulated supply input as specified by its manufacturer. A bare ESP8266 requires regulated 3.3 V power and appropriate logic level shifting. Configure the module's AT-command UART to **9600 baud**; the sketch assumes this is already set.
5. Open the `rgb-strip-firmware` sketch folder in Arduino IDE, select **Arduino Uno** and the matching port, then build and upload.
6. Open **Tools > Serial Monitor** at **9600 baud**. Press the Uno's reset button to see startup before Wi-Fi initialization, each connection attempt, failures, the assigned IP after connecting, and detected disconnections. Reconnection attempts also print the current IP after succeeding. The HTTP server uses port 80.

## Raspberry Pi web controller

The browser controller uses Python 3.9 or newer and only the Python standard library. Start it on the Raspberry Pi, replacing the address with the firmware's IP address:

```sh
python3 web/server.py --arduino-url http://192.168.1.50 --host 0.0.0.0 --port 8180
```

Open `http://<pi-ip>:8180/home.html` from a browser on the same trusted local network. The page can set a color, turn the strip off, or start the rainbow in either direction. It sends no LED command when the server starts or when the page opens.

For a physical demo, check that the server starts without an error, then use **Set Color**, **Turn Off**, **Start Rainbow** in Forward, and **Start Rainbow** in Backward. Confirm that the strip responds to each command and that the page reports whether the controller accepted it.

This server has no authentication. Keep it on a trusted local network and do not expose port 8180 to the public internet.
