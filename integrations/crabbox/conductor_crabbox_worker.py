#!/usr/bin/env python3
"""Minimal Conductor SIMPLE worker backed by `crabbox run`.

This worker intentionally uses only the standard library. It is an example
bridge, not a replacement for the official Conductor SDK worker runtimes.
"""

from __future__ import annotations

import argparse
import json
import os
import socket
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

from crabbox_bridge import CrabboxRequest, run_crabbox


def main() -> None:
    parser = argparse.ArgumentParser(description="Poll Conductor tasks and execute them via Crabbox.")
    parser.add_argument("--server", default=os.getenv("CONDUCTOR_SERVER_URL", "http://localhost:8080"))
    parser.add_argument("--task-type", default=os.getenv("CONDUCTOR_CRABBOX_TASK_TYPE", "crabbox_run"))
    parser.add_argument("--domain", default=os.getenv("CONDUCTOR_CRABBOX_DOMAIN"))
    parser.add_argument("--worker-id", default=os.getenv("CONDUCTOR_WORKER_ID") or default_worker_id())
    parser.add_argument("--poll-interval", type=float, default=float(os.getenv("CONDUCTOR_POLL_INTERVAL_SECONDS", "2")))
    parser.add_argument("--long-poll-ms", type=int, default=int(os.getenv("CONDUCTOR_LONG_POLL_MS", "1000")))
    parser.add_argument("--heartbeat-seconds", type=int, default=int(os.getenv("CONDUCTOR_HEARTBEAT_SECONDS", "30")))
    parser.add_argument("--once", action="store_true", help="Process at most one task and exit.")
    args = parser.parse_args()

    client = ConductorClient(args.server, args.worker_id)
    print(f"polling taskType={args.task_type} domain={args.domain or '<none>'} workerId={args.worker_id}")

    while True:
        task = client.poll(args.task_type, args.domain, args.long_poll_ms)
        if task:
            process_task(client, task, args.heartbeat_seconds)
            if args.once:
                return
        else:
            if args.once:
                return
            time.sleep(args.poll_interval)


def process_task(client: "ConductorClient", task: dict[str, Any], heartbeat_seconds: int) -> None:
    task_id = task["taskId"]
    workflow_id = task["workflowInstanceId"]
    input_data = task.get("inputData") or {}
    last_heartbeat = 0.0

    def heartbeat(output_tail: str = "") -> None:
        nonlocal last_heartbeat
        now = time.monotonic()
        if now - last_heartbeat < heartbeat_seconds:
            return
        last_heartbeat = now
        client.update_task(
            workflow_id,
            task_id,
            "IN_PROGRESS",
            {
                "phase": "running",
                "provider": input_data.get("provider", "local-container"),
                "outputTail": output_tail[-4000:],
            },
            callback_after_seconds=heartbeat_seconds * 2,
        )

    try:
        request = CrabboxRequest.from_mapping(input_data)
        seen_lines: list[str] = []

        def on_line(line: str) -> None:
            seen_lines.append(line)
            if len(seen_lines) > 20:
                del seen_lines[:-20]
            client.add_log(task_id, line[:4000])
            heartbeat("\n".join(seen_lines))

        heartbeat()
        result = run_crabbox(request, on_line=on_line)
        client.update_task(
            workflow_id,
            task_id,
            result["status"],
            result,
            reason_for_incompletion=None if result["status"] == "COMPLETED" else result["reason"],
        )
    except Exception as exc:
        client.update_task(
            workflow_id,
            task_id,
            "FAILED_WITH_TERMINAL_ERROR",
            {"error": str(exc), "errorType": exc.__class__.__name__},
            reason_for_incompletion=str(exc),
        )


class ConductorClient:
    def __init__(self, server: str, worker_id: str) -> None:
        self.server = server.rstrip("/")
        self.worker_id = worker_id
        self.auth_token = os.getenv("CONDUCTOR_AUTH_TOKEN")

    def poll(self, task_type: str, domain: str | None, timeout_ms: int) -> dict[str, Any] | None:
        query = {"workerid": self.worker_id, "timeout": str(timeout_ms)}
        if domain:
            query["domain"] = domain
        path = f"/api/tasks/poll/{urllib.parse.quote(task_type)}?{urllib.parse.urlencode(query)}"
        try:
            response = self._request("GET", path)
        except urllib.error.HTTPError as exc:
            if exc.code == 204:
                return None
            raise
        if response is None:
            return None
        return json.loads(response)

    def update_task(
        self,
        workflow_id: str,
        task_id: str,
        status: str,
        output_data: dict[str, Any],
        *,
        reason_for_incompletion: str | None = None,
        callback_after_seconds: int | None = None,
    ) -> None:
        payload: dict[str, Any] = {
            "workflowInstanceId": workflow_id,
            "taskId": task_id,
            "status": status,
            "outputData": output_data,
        }
        if reason_for_incompletion:
            payload["reasonForIncompletion"] = reason_for_incompletion
        if callback_after_seconds is not None:
            payload["callbackAfterSeconds"] = callback_after_seconds
        self._request("POST", "/api/tasks", payload)

    def add_log(self, task_id: str, line: str) -> None:
        if not line:
            return
        self._request("POST", f"/api/tasks/{urllib.parse.quote(task_id)}/log", line, content_type="text/plain")

    def _request(
        self,
        method: str,
        path: str,
        payload: Any | None = None,
        *,
        content_type: str = "application/json",
    ) -> str | None:
        data = None
        if payload is not None:
            if content_type == "application/json":
                data = json.dumps(payload).encode("utf-8")
            else:
                data = str(payload).encode("utf-8")
        request = urllib.request.Request(f"{self.server}{path}", data=data, method=method)
        request.add_header("accept", "application/json")
        request.add_header("content-type", content_type)
        if self.auth_token:
            request.add_header("authorization", f"Bearer {self.auth_token}")
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                body = response.read()
                return body.decode("utf-8") if body else None
        except urllib.error.HTTPError as exc:
            if exc.code == 204:
                return None
            raise


def default_worker_id() -> str:
    return f"crabbox-{socket.gethostname()}-{os.getpid()}"


if __name__ == "__main__":
    main()
