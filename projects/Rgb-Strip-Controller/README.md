# RGB Strip Controller

This project contains the Android controller app and Arduino firmware for an RGB LED strip.

## Components

- Desktop app — Windows and Linux x64/ARM64 desktop controller.
- [Android app](android/README.md) — Android Studio setup, build, lint, and app behavior.
- [Firmware sketch](rgb-strip-firmware/rgb-strip-firmware.ino) — Arduino firmware source.

The app communicates with the firmware over the local network using the documented `/rgb` and `/rainbow` HTTP endpoints. Configure the firmware network credentials for your environment before uploading it.

## Desktop app

The desktop app sends no network request on startup. Enter the firmware's address, then use **Apply** or **Start Rainbow**. Turning power off and pressing **Apply** sends zero RGB values while keeping the chosen color in the app.

Build and publish from this directory with the .NET 10 SDK:

```powershell
dotnet build RgbStripController.slnx -c Release
dotnet publish RgbStripController.Desktop/RgbStripController.Desktop.csproj -c Release -r win-x64 --self-contained true -p:PublishTrimmed=false -p:PublishAot=false -o publish/win-x64
dotnet publish RgbStripController.Desktop/RgbStripController.Desktop.csproj -c Release -r linux-x64 --self-contained true -p:PublishTrimmed=false -p:PublishAot=false -o publish/linux-x64
dotnet publish RgbStripController.Desktop/RgbStripController.Desktop.csproj -c Release -r linux-arm64 --self-contained true -p:PublishTrimmed=false -p:PublishAot=false -o publish/linux-arm64
```

The publish folders contain self-contained applications. On Windows, launch `publish/win-x64/RgbStripController.Desktop.exe`. On Linux x64 or Raspberry Pi ARM64, use a 64-bit desktop OS with an X11 or XWayland display, install the Avalonia Linux libraries (`sudo apt install libx11-6 libice6 libsm6 libfontconfig1` on Debian-based systems), then run `./RgbStripController.Desktop` from the matching Linux publish folder. If needed, grant execute permission first with `chmod +x ./RgbStripController.Desktop`.

The app stores settings in `LocalApplicationData/RgbStripController/settings.json` (on Windows, `%LOCALAPPDATA%\RgbStripController\settings.json`; on Linux, `~/.local/share/RgbStripController/settings.json`). The file is written when the window closes.

## Firmware setup

1. Install the `Adafruit NeoPixel`, `WiFiEsp`, and `aWOT` Arduino libraries.
2. Edit the sketch's `ssid` and `password` values for the Wi-Fi network the controller should join.
3. Open the `rgb-strip-firmware` sketch folder in Arduino IDE, select the matching board and port, then build and upload.

The sketch holds Wi-Fi credentials, HTTP routes, and server polling. `HttpHelpers.h/.cpp` contains request validation helpers; `LedController.h/.cpp` owns the strip and animation; `WiFiController.h/.cpp` owns Wi-Fi connection and reconnection. Keep these files together in the sketch folder. Disable WiFiEsp logging by setting `_ESPLOGLEVEL_` to `0` in its `src/utility/debug.h`: the ESP8266 uses the hardware serial port for AT commands.

Keep the phone and controller on the same local network. The sketch listens on port 80; configure the app with the controller's reachable address.
