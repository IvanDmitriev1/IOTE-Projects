# Home Invasion Detection System

The Raspberry Pi detects motion, captures snapshots, and publishes events. The Android app displays events and opens them from notifications.

## Raspberry Pi

On the Pi desktop, install Tkinter, Pillow, Picamera2, and a Pi-compatible RPi.GPIO implementation. Then install the remaining dependencies and run the app:

```sh
python -m pip install -r requirements.txt
python home_invasion.py
```

The script watches physical pin 7 and waits at least 30 seconds between capture attempts.
