#!/usr/bin/env python3
"""Serve a small LAN web controller for the existing RGB strip firmware."""

import argparse
import http.client
import json
import re
import socket
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request
from email.message import Message
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


MAX_REQUEST_BYTES = 1024
UPSTREAM_TIMEOUT_SECONDS = 5
REQUEST_TIMEOUT_SECONDS = 5


class InvalidCommand(ValueError):
    pass


class UpstreamTimeout(Exception):
    pass


class UpstreamFailure(Exception):
    pass


def validate_arduino_url(value):
    if not isinstance(value, str) or any(
        character.isspace() or ord(character) < 33 or ord(character) == 127
        for character in value
    ):
        raise ValueError("Arduino URL must be a valid HTTP base address.")

    try:
        parsed = urllib.parse.urlsplit(value)
        port = parsed.port
    except ValueError as error:
        raise ValueError("Arduino URL must be a valid HTTP base address.") from error

    if (
        parsed.scheme.lower() != "http"
        or not parsed.netloc
        or not parsed.hostname
        or "@" in parsed.netloc
        or parsed.netloc.endswith(":")
        or parsed.path not in ("", "/")
        or "?" in value
        or "#" in value
        or (port is not None and not 1 <= port <= 65535)
    ):
        raise ValueError("Arduino URL must be a valid HTTP base address.")

    return f"http://{parsed.netloc}"


def command_path(payload):
    if not isinstance(payload, dict):
        raise InvalidCommand("Request body must be a JSON object.")

    action = payload.get("action")
    if action == "off":
        return "/rgb?r=0&g=0&b=0"

    if action == "color":
        channels = (payload.get("r"), payload.get("g"), payload.get("b"))
        if any(type(value) is not int or not 0 <= value <= 255 for value in channels):
            raise InvalidCommand("Color channels must be integers from 0 to 255.")
        return "/rgb?" + urllib.parse.urlencode(
            {"r": channels[0], "g": channels[1], "b": channels[2]}
        )

    if action == "rainbow":
        direction = payload.get("direction")
        if type(direction) is not int or direction not in (1, 2):
            raise InvalidCommand("Rainbow direction must be 1 or 2.")
        return "/rainbow?" + urllib.parse.urlencode({"direction": direction})

    raise InvalidCommand("Unsupported action.")


def reject_json_constant(value):
    raise ValueError(f"Invalid JSON constant: {value}")


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, file_pointer, code, message, headers, new_url):
        return None


class ArduinoClient:
    def __init__(self, base_url, timeout=UPSTREAM_TIMEOUT_SECONDS):
        self.base_url = validate_arduino_url(base_url)
        self.timeout = timeout
        self.opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}), NoRedirectHandler()
        )

    def send(self, path):
        request = urllib.request.Request(self.base_url + path, method="GET")
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                if not 200 <= response.getcode() < 300:
                    raise UpstreamFailure("Arduino returned an unsuccessful status.")
        except urllib.error.HTTPError as error:
            error.close()
            raise UpstreamFailure("Arduino returned an unsuccessful status.") from error
        except urllib.error.URLError as error:
            if isinstance(error.reason, (TimeoutError, socket.timeout)):
                raise UpstreamTimeout from error
            raise UpstreamFailure("Unable to reach Arduino.") from error
        except (TimeoutError, socket.timeout) as error:
            raise UpstreamTimeout from error
        except (OSError, http.client.HTTPException, ValueError) as error:
            raise UpstreamFailure("Unable to reach Arduino.") from error


class ControllerHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def setup(self):
        super().setup()
        self.connection.settimeout(self.server.request_timeout)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, format, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), format % args))

    def log_request(self, code="-", size="-"):
        path = self._route_path()
        safe_path = path if path in ("/", "/home.html", "/control") else "unknown"
        self.log_message("%s %s %s", self.command or "-", safe_path, code)

    def __getattr__(self, name):
        if name.startswith("do_"):
            return self._unsupported_method
        raise AttributeError(name)

    def _route_path(self):
        try:
            return urllib.parse.urlsplit(self.path).path
        except ValueError:
            return ""

    def _send_json(self, status, payload, allow=None):
        body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        if allow:
            self.send_header("Allow", allow)
        self.end_headers()
        self.wfile.write(body)

    def _error(self, status, message, allow=None):
        self._send_json(status, {"ok": False, "error": message}, allow)

    def _not_found(self):
        self._error(404, "Not found.")

    def _method_not_allowed(self, allow):
        self._error(405, "Method not allowed.", allow)

    def _unsupported_method(self):
        path = self._route_path()
        if path in ("/", "/home.html"):
            self._method_not_allowed("GET")
        elif path == "/control":
            self._method_not_allowed("POST")
        else:
            self._not_found()

    def do_GET(self):
        path = self._route_path()
        if path in ("/", "/home.html"):
            try:
                body = Path(__file__).with_name("home.html").read_bytes()
            except OSError:
                self._error(500, "Controller page is unavailable.")
                return
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif path == "/control":
            self._method_not_allowed("POST")
        else:
            self._not_found()

    def do_POST(self):
        path = self._route_path()
        if path != "/control":
            if path in ("/", "/home.html"):
                self._method_not_allowed("GET")
            else:
                self._not_found()
            return

        payload = self._read_json_body()
        if payload is None:
            return
        try:
            upstream_path = command_path(payload)
        except InvalidCommand as error:
            self._error(400, str(error))
            return

        try:
            with self.server.upstream_lock:
                self.server.arduino.send(upstream_path)
        except UpstreamTimeout:
            self.log_message("upstream timeout")
            self._error(504, "Arduino request timed out.")
            return
        except UpstreamFailure:
            self.log_message("upstream request failed")
            self._error(502, "Unable to reach Arduino.")
            return

        self._send_json(200, {"ok": True})

    def _read_json_body(self):
        if self.headers.get_all("Transfer-Encoding"):
            self.close_connection = True
            self._error(400, "Transfer-Encoding is not supported.")
            return None

        lengths = self.headers.get_all("Content-Length") or []
        if len(lengths) != 1 or not re.fullmatch(r"[0-9]+", lengths[0].strip()):
            self.close_connection = True
            self._error(400, "A valid Content-Length is required.")
            return None

        normalized_length = lengths[0].strip().lstrip("0") or "0"
        if (
            len(normalized_length) > len(str(MAX_REQUEST_BYTES))
            or int(normalized_length) > MAX_REQUEST_BYTES
        ):
            self.close_connection = True
            self._error(413, "Request body is too large.")
            return None
        length = int(normalized_length, 10)

        content_types = self.headers.get_all("Content-Type") or []
        if len(content_types) != 1 or not self._is_json_content_type(content_types[0]):
            self.close_connection = True
            self._error(415, "Content-Type must be application/json.")
            return None

        try:
            body = self.rfile.read(length)
        except (OSError, TimeoutError):
            self.close_connection = True
            self._error(400, "Request body was incomplete.")
            return None
        if len(body) != length:
            self.close_connection = True
            self._error(400, "Request body was incomplete.")
            return None

        try:
            text = body.decode("utf-8")
            payload = json.loads(text, parse_constant=reject_json_constant)
        except (UnicodeDecodeError, json.JSONDecodeError, ValueError):
            self._error(400, "Request body must contain valid UTF-8 JSON.")
            return None
        if not isinstance(payload, dict):
            self._error(400, "Request body must be a JSON object.")
            return None
        return payload

    @staticmethod
    def _is_json_content_type(value):
        header = Message()
        try:
            header["content-type"] = value
            parameters = header.get_params()
        except (TypeError, ValueError):
            return False
        if not parameters or header.get_content_type().lower() != "application/json":
            return False
        extra = parameters[1:]
        return len(extra) <= 1 and all(
            name.lower() == "charset" and bool(parameter)
            for name, parameter in extra
        )


class ControllerServer(ThreadingHTTPServer):
    allow_reuse_address = True

    def __init__(
        self,
        server_address,
        arduino_url,
        upstream_timeout=UPSTREAM_TIMEOUT_SECONDS,
        request_timeout=REQUEST_TIMEOUT_SECONDS,
    ):
        self.arduino = ArduinoClient(arduino_url, timeout=upstream_timeout)
        self.upstream_lock = threading.Lock()
        self.request_timeout = request_timeout
        super().__init__(server_address, ControllerHandler)


def parse_port(value):
    try:
        port = int(value, 10)
    except ValueError as error:
        raise argparse.ArgumentTypeError("port must be an integer from 1 to 65535") from error
    if not 1 <= port <= 65535:
        raise argparse.ArgumentTypeError("port must be an integer from 1 to 65535")
    return port


def main(argv=None):
    parser = argparse.ArgumentParser(description="Serve the RGB strip browser controller.")
    parser.add_argument("--arduino-url", required=True, help="Arduino HTTP base address")
    parser.add_argument("--host", default="0.0.0.0", help="interface to listen on")
    parser.add_argument("--port", type=parse_port, default=8180, help="HTTP port (default: 8180)")
    args = parser.parse_args(argv)
    try:
        arduino_url = validate_arduino_url(args.arduino_url)
    except ValueError as error:
        parser.error(str(error))
    if not args.host or any(character.isspace() for character in args.host):
        parser.error("host must be a non-empty hostname or address")

    try:
        server = ControllerServer((args.host, args.port), arduino_url)
    except OSError as error:
        print("Unable to start the web controller.", file=sys.stderr)
        return 1

    print(f"RGB strip controller listening on http://{args.host}:{server.server_port}/")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Shutting down RGB strip controller.")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
