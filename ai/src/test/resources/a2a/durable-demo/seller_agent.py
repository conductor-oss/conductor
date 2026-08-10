#!/usr/bin/env python3
"""A 'restaurant' A2A seller agent with a slow order — dependency-free (stdlib only).

Speaks the A2A JSON-RPC wire protocol. message/send creates a task that stays WORKING for
SELLER_DELAY seconds (the agent is 'preparing the order'), then COMPLETES with a receipt. The
window is what lets us kill & restart Conductor mid-order in the durability demo. Task state is
computed from elapsed time, and this agent is a SEPARATE process that keeps running (and keeps its
per-task start times) across the Conductor restart — which is exactly why the order survives.

Run:  SELLER_DELAY=25 python3 seller_agent.py     # serves http://localhost:9999
"""
import json
import os
import time
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer

DELAY = float(os.environ.get("SELLER_DELAY", "25"))
PORT = int(os.environ.get("SELLER_PORT", "9999"))
TASKS = {}  # taskId -> (start_epoch, order_text)

CARD = {
    "name": "Restaurant Agent",
    "description": "A slow restaurant A2A agent for the durable-A2A demo",
    "url": f"http://localhost:{PORT}/",
    "version": "1.0.0",
    "protocolVersion": "0.3.0",
    "capabilities": {"streaming": False, "pushNotifications": False},
    "defaultInputModes": ["text/plain"],
    "defaultOutputModes": ["text/plain"],
    "skills": [
        {
            "id": "place_order",
            "name": "Place order",
            "description": "Takes a food order and returns a confirmed receipt",
            "tags": ["ordering", "demo"],
        }
    ],
}


def _text(message):
    parts = (message or {}).get("parts") or []
    return "\n".join(p.get("text", "") for p in parts if p.get("text")) or "your order"


def _task(task_id, state, receipt):
    result = {
        "kind": "task",
        "id": task_id,
        "contextId": "ctx-" + task_id[:8],
        "status": {"state": state},
    }
    if receipt:
        result["artifacts"] = [
            {"artifactId": "receipt", "name": "receipt",
             "parts": [{"kind": "text", "text": receipt}]}
        ]
    return result


def _task_state(task_id):
    if task_id not in TASKS:
        return _task(task_id, "failed", None)
    start, order = TASKS[task_id]
    if time.time() - start >= DELAY:
        order_id = "ORD-" + uuid.uuid4().hex[:8].upper()
        return _task(task_id, "completed", f"Order {order_id} confirmed: {order}. Enjoy!")
    return _task(task_id, "working", None)


class Handler(BaseHTTPRequestHandler):
    def _send(self, obj, code=200):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.endswith("/.well-known/agent-card.json") or self.path.endswith(
            "/.well-known/agent.json"
        ):
            self._send(CARD)
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        req = json.loads(self.rfile.read(length) or b"{}")
        method = req.get("method")
        params = req.get("params") or {}
        rid = req.get("id")
        if method == "message/send":
            task_id = uuid.uuid4().hex
            TASKS[task_id] = (time.time(), _text(params.get("message")))
            self._send({"jsonrpc": "2.0", "id": rid, "result": _task(task_id, "working", None)})
        elif method == "tasks/get":
            self._send({"jsonrpc": "2.0", "id": rid, "result": _task_state(params.get("id"))})
        elif method == "tasks/cancel":
            TASKS.pop(params.get("id"), None)
            self._send(
                {"jsonrpc": "2.0", "id": rid, "result": _task(params.get("id"), "canceled", None)}
            )
        else:
            self._send(
                {"jsonrpc": "2.0", "id": rid,
                 "error": {"code": -32601, "message": "method not found: " + str(method)}}
            )

    def log_message(self, *args):
        pass  # quiet


if __name__ == "__main__":
    print(f"Restaurant agent on http://localhost:{PORT} (SELLER_DELAY={DELAY}s)")
    HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
