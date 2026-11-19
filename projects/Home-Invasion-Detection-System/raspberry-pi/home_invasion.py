import tkinter as tk
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from tempfile import TemporaryDirectory
from time import monotonic, time_ns
from uuid import uuid4

import firebase_admin
from firebase_admin import credentials, db, messaging, storage
from picamera2 import Picamera2
from PIL import Image, ImageTk
import RPi.GPIO as GPIO

from publish_event import publish_event


PIR_PIN = 7  # Physical header pin (GPIO.BOARD numbering).
COOLDOWN_SECONDS = 30
DATABASE_URL = "https://home-invasion-detection-system-default-rtdb.europe-west1.firebasedatabase.app"
STORAGE_BUCKET = "home-invasion-detection-system.firebasestorage.app"


def main():
    service_account = Path(__file__).resolve().with_name("service-account.json")
    if not service_account.is_file():
        raise SystemExit("Put service-account.json next to home_invasion.py.")
    firebase_admin.initialize_app(credentials.Certificate(str(service_account)), options={
        "databaseURL": DATABASE_URL,
        "storageBucket": STORAGE_BUCKET,
    })
    bucket = storage.bucket()
    root = tk.Tk()
    root.title("Home Invasion Detection")
    root.resizable(False, False)
    status = tk.Label(root, text="Waiting for movement…", font=("Calibri", 32))
    status.pack(padx=16, pady=12)
    preview = tk.Label(root)
    preview.pack(padx=16, pady=12)

    with Picamera2() as camera, ThreadPoolExecutor(max_workers=1) as worker:
        config = camera.create_still_configuration(main={"size": (800, 600)})
        capture = None
        next_capture = 0.0

        def capture_event(detected_at_ms):
            with TemporaryDirectory() as folder:
                path = Path(folder) / "snapshot.jpg"
                camera.start_and_capture_file(str(path), show_preview=False, capture_mode=config)
                with Image.open(path) as image:
                    snapshot = image.copy()
                try:
                    event_id = uuid4().hex
                    publish_event(db, bucket, "demo", event_id, detected_at_ms, str(path))
                except Exception as error:
                    snapshot.close()
                    raise RuntimeError(f"Could not publish snapshot: {error}") from error
                try:
                    message_id = messaging.send(messaging.Message(
                        topic="home-demo",
                        data={"type": "motion", "homeId": "demo", "eventId": event_id},
                        android=messaging.AndroidConfig(priority="high"),
                    ))
                    print(f"FCM accepted alert for {event_id}: {message_id}", flush=True)
                except Exception as error:
                    snapshot.close()
                    raise RuntimeError(f"Snapshot saved, but notification failed: {error}") from error
                return snapshot

        def poll():
            nonlocal capture, next_capture
            if capture is not None and capture.done():
                try:
                    with capture.result() as snapshot:
                        preview.image = ImageTk.PhotoImage(snapshot)
                    preview.configure(image=preview.image)
                    status.configure(text="Motion event sent", fg="dark green")
                except Exception as error:
                    print(error, flush=True)
                    status.configure(text=str(error), fg="red", wraplength=750)
                capture = None
            if GPIO.event_detected(PIR_PIN) and capture is None and monotonic() >= next_capture:
                next_capture = monotonic() + COOLDOWN_SECONDS
                status.configure(text="Movement detected…", fg="red")
                capture = worker.submit(capture_event, time_ns() // 1_000_000)
            root.after(100, poll)

        GPIO.setmode(GPIO.BOARD)
        try:
            GPIO.setup(PIR_PIN, GPIO.IN)
            GPIO.add_event_detect(PIR_PIN, GPIO.RISING)
            root.after(100, poll)
            root.protocol("WM_DELETE_WINDOW", root.quit)
            root.mainloop()
        finally:
            GPIO.cleanup()
            root.destroy()


if __name__ == "__main__":
    main()
