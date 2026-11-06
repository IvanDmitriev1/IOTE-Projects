"""Forward local weather-station MQTT readings to ThingSpeak."""

import json
import logging
import math
import re
import subprocess
import sys
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

BROKER_HOST = "127.0.0.1"
BROKER_PORT = 1883
MQTT_TOPIC = "channel1"
THINGSPEAK_URL = "https://api.thingspeak.com/update"
THINGSPEAK_WRITE_KEY = "7RS8C1NN7OE4W8EU"
UPLOAD_INTERVAL = 30
UPLOAD_TIMEOUT = 5
MAX_PAYLOAD_BYTES = 16 * 1024
MAX_REPLY_BYTES = 64
ASCII_NUMBER = re.compile(r"[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?")

logger = logging.getLogger(__name__)


def parse_reading(payload):
    if not isinstance(payload, (bytes, str)) or len(payload) > MAX_PAYLOAD_BYTES:
        raise ValueError("invalid payload size")
    try:
        reading = json.loads(payload, parse_float=str)
    except (TypeError, ValueError, RecursionError) as error:
        raise ValueError("invalid JSON") from error
    if not isinstance(reading, dict):
        raise ValueError("expected a JSON object")

    fields = {}
    for key, field in zip(("t", "h", "p", "l"), range(1, 5)):
        value = reading.get(key)
        if isinstance(value, bool) or not isinstance(value, (int, float, str)):
            raise ValueError("expected four finite numeric readings")
        text = value.strip() if isinstance(value, str) else str(value)
        if not ASCII_NUMBER.fullmatch(text) or not math.isfinite(float(text)):
            raise ValueError("expected four finite numeric readings")
        fields[f"field{field}"] = text
    return fields


def send_to_thingspeak(fields):
    query = urlencode({"api_key": THINGSPEAK_WRITE_KEY, **fields}).encode("ascii")
    # The process timeout also covers DNS and a response that trickles bytes.
    reply = subprocess.run(
        [sys.executable, __file__, "--upload"],
        input=query,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        timeout=UPLOAD_TIMEOUT,
        check=True,
    ).stdout
    if len(reply) > MAX_REPLY_BYTES:
        logger.warning("ThingSpeak rejected the update (oversized reply)")
        return None
    result = reply.strip()
    if result.startswith(b"ERROR "):
        logger.warning("ThingSpeak upload failed (%s)", result[6:].decode("ascii"))
        return None
    if not result.isdigit() or int(result) <= 0:
        logger.warning("ThingSpeak rejected the update")
        return None
    return int(result)


def upload_from_stdin():
    try:
        query = sys.stdin.buffer.read().decode("ascii")
        request = Request(f"{THINGSPEAK_URL}?{query}", method="GET")
        with urlopen(request, timeout=UPLOAD_TIMEOUT) as response:
            reply = response.read(MAX_REPLY_BYTES + 1)
    except HTTPError as error:
        reply = f"ERROR HTTP {error.code}".encode("ascii")
    except URLError as error:
        reply = f"ERROR {type(error.reason).__name__}".encode("ascii")
    except Exception as error:
        reply = f"ERROR {type(error).__name__}".encode("ascii")
    else:
        # Only the worker may produce diagnostic messages.
        if reply.strip().startswith(b"ERROR "):
            reply = b"0"
    sys.stdout.buffer.write(reply)


class ThingSpeakBridge:
    def __init__(self):
        self._lock = threading.Lock()
        self._sums = [0.0] * 4
        self._count = 0

    def on_connect(self, client, userdata, connect_flags, reason_code, properties):
        if reason_code != 0:
            logger.warning("MQTT connection failed (%s)", reason_code)
            return
        result, _ = client.subscribe(MQTT_TOPIC, qos=0)
        if result != 0:
            logger.warning("MQTT subscription failed (%s)", result)

    def on_message(self, client, userdata, message):
        if message.retain:
            return
        try:
            fields = parse_reading(message.payload)
        except ValueError:
            logger.warning("Ignoring malformed MQTT reading")
            return

        values = [float(fields[f"field{i}"]) for i in range(1, 5)]
        with self._lock:
            self._sums = [total + value for total, value in zip(self._sums, values)]
            self._count += 1

    def upload_window(self):
        with self._lock:
            count, sums = self._count, self._sums
            self._count, self._sums = 0, [0.0] * 4
        if not count:
            return

        fields = {f"field{i}": format(total / count, ".15g") for i, total in enumerate(sums, 1)}
        if not all(math.isfinite(float(value)) for value in fields.values()):
            logger.warning("Discarding nonfinite ThingSpeak window")
            return

        try:
            entry_id = send_to_thingspeak(fields)
        except Exception as error:
            logger.warning("ThingSpeak upload failed (%s)", type(error).__name__)
            return
        if entry_id is not None:
            logger.info("ThingSpeak accepted entry %d", entry_id)


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    import paho.mqtt.client as mqtt

    bridge = ThingSpeakBridge()
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
    client.on_connect = bridge.on_connect
    client.on_message = bridge.on_message
    client.connect_async(BROKER_HOST, BROKER_PORT, keepalive=60)
    client.loop_start()
    deadline = time.monotonic() + UPLOAD_INTERVAL
    try:
        while True:
            time.sleep(max(0, deadline - time.monotonic()))
            bridge.upload_window()
            deadline += UPLOAD_INTERVAL
            now = time.monotonic()
            while deadline <= now:
                deadline += UPLOAD_INTERVAL
    except KeyboardInterrupt:
        pass
    finally:
        client.disconnect()
        client.loop_stop()


if __name__ == "__main__":
    if sys.argv[1:] == ["--upload"]:
        upload_from_stdin()
    else:
        main()
