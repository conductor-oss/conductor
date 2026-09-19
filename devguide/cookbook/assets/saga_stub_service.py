"""A tiny stand-in for the three services the saga calls.

Run it before starting the workflow:

    python3 saga_stub_service.py          # listens on http://localhost:8088

Endpoints
    POST /inventory/reserve   -> 200, returns a reservationId
    POST /payments/charge     -> 200, returns a chargeId
    POST /shipping/book       -> status taken from the ?status= query (default 200)
    POST /payments/refund     -> 200
    POST /inventory/release   -> 200
    GET  /calls               -> every call received, so you can prove what ran
    POST /reset               -> clear the call log

Each write endpoint is idempotent on the Idempotency-Key header: a repeat of a
key it has already seen is acknowledged without doing the work twice.
"""

import json
from http.server import BaseHTTPRequestHandler, HTTPServer

CALLS = []
SEEN_KEYS = {}


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, payload):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith("/calls"):
            self._send(200, {"calls": CALLS})
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):
        path = self.path.split("?")[0]
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            body = json.loads(raw or b"{}")
        except ValueError:
            body = {"raw": raw.decode(errors="replace")}
        key = self.headers.get("Idempotency-Key")

        if path == "/reset":
            CALLS.clear()
            SEEN_KEYS.clear()
            self._send(200, {"reset": True})
            return

        # Idempotency: same key, same answer, no repeated work.
        if key and key in SEEN_KEYS:
            CALLS.append({"path": path, "key": key, "replayed": True})
            self._send(200, SEEN_KEYS[key])
            return

        if path == "/shipping/book":
            status = 200
            if "status=" in self.path:
                try:
                    status = int(self.path.split("status=")[1].split("&")[0])
                except ValueError:
                    status = 200
            CALLS.append({"path": path, "key": key, "status": status, "body": body})
            if status >= 400:
                self._send(status, {"error": "carrier unavailable"})
                return
            result = {"shipmentId": f"SHP-{len(CALLS)}"}
        elif path == "/inventory/reserve":
            result = {"reservationId": f"RES-{len(CALLS) + 1}"}
            CALLS.append({"path": path, "key": key, "body": body})
        elif path == "/payments/charge":
            result = {"chargeId": f"CHG-{len(CALLS) + 1}"}
            CALLS.append({"path": path, "key": key, "body": body})
        elif path in ("/payments/refund", "/inventory/release"):
            result = {"undone": True, "path": path}
            CALLS.append({"path": path, "key": key, "body": body})
        else:
            self._send(404, {"error": "not found"})
            return

        if key:
            SEEN_KEYS[key] = result
        self._send(200, result)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print("saga stub listening on http://localhost:8088")
    HTTPServer(("127.0.0.1", 8088), Handler).serve_forever()
