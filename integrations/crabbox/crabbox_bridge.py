#!/usr/bin/env python3
"""Shared Crabbox runner helpers for Conductor integration examples."""

from __future__ import annotations

import json
import os
import shlex
import subprocess
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_LIMIT = 200_000
DEFAULT_TAIL_LINES = 120


@dataclass
class CrabboxRequest:
    """Validated subset of the Crabbox run surface exposed to workflows."""

    command: str | list[str]
    provider: str = "local-container"
    shell: bool = False
    workspace_dir: str | None = None
    profile: str | None = None
    lease_id: str | None = None
    machine_class: str | None = None
    target: str | None = None
    arch: str | None = None
    ttl: str | None = None
    idle_timeout: str | None = None
    keep: bool = False
    keep_on_failure: bool = False
    no_sync: bool = False
    full_resync: bool = False
    preflight: bool = False
    timing_json: bool = True
    timeout_seconds: int | None = None
    extra_env: dict[str, str] = field(default_factory=dict)

    @classmethod
    def from_mapping(cls, data: dict[str, Any]) -> "CrabboxRequest":
        command = data.get("command")
        if not command:
            raise ValueError("input 'command' is required")
        if not isinstance(command, (str, list)):
            raise ValueError("input 'command' must be a string or list of strings")
        if isinstance(command, list) and not all(isinstance(part, str) for part in command):
            raise ValueError("input 'command' list must contain only strings")

        extra_env = data.get("env") or data.get("extraEnv") or {}
        if not isinstance(extra_env, dict):
            raise ValueError("input 'env' must be an object when provided")

        timeout_seconds = data.get("timeoutSeconds")
        if timeout_seconds is not None:
            timeout_seconds = int(timeout_seconds)

        return cls(
            command=command,
            provider=str(data.get("provider") or "local-container"),
            shell=bool(data.get("shell", isinstance(command, str))),
            workspace_dir=data.get("workspaceDir") or data.get("repoPath"),
            profile=data.get("profile"),
            lease_id=data.get("leaseId") or data.get("id"),
            machine_class=data.get("class") or data.get("machineClass"),
            target=data.get("target"),
            arch=data.get("arch"),
            ttl=data.get("ttl"),
            idle_timeout=data.get("idleTimeout"),
            keep=bool(data.get("keep", False)),
            keep_on_failure=bool(data.get("keepOnFailure", False)),
            no_sync=bool(data.get("noSync", False)),
            full_resync=bool(data.get("fullResync", False) or data.get("freshSync", False)),
            preflight=bool(data.get("preflight", False)),
            timing_json=bool(data.get("timingJson", True)),
            timeout_seconds=timeout_seconds,
            extra_env={str(key): str(value) for key, value in extra_env.items()},
        )


def build_crabbox_command(request: CrabboxRequest) -> list[str]:
    cmd = ["crabbox", "run", "--provider", request.provider]

    optional_flags = [
        ("--profile", request.profile),
        ("--id", request.lease_id),
        ("--class", request.machine_class),
        ("--target", request.target),
        ("--arch", request.arch),
        ("--ttl", request.ttl),
        ("--idle-timeout", request.idle_timeout),
    ]
    for flag, value in optional_flags:
        if value:
            cmd.extend([flag, str(value)])

    if request.keep:
        cmd.append("--keep")
    if request.keep_on_failure:
        cmd.append("--keep-on-failure")
    if request.no_sync:
        cmd.append("--no-sync")
    if request.full_resync:
        cmd.append("--full-resync")
    if request.preflight:
        cmd.append("--preflight")
    if request.timing_json:
        cmd.append("--timing-json")

    if request.shell:
        shell_command = (
            request.command
            if isinstance(request.command, str)
            else " ".join(shlex.quote(part) for part in request.command)
        )
        cmd.extend(["--shell", shell_command])
    else:
        if isinstance(request.command, str):
            cmd.extend(["--", request.command])
        else:
            cmd.extend(["--", *request.command])
    return cmd


def run_crabbox(
    request: CrabboxRequest,
    *,
    on_line: Any | None = None,
    output_limit: int = DEFAULT_OUTPUT_LIMIT,
) -> dict[str, Any]:
    cwd = Path(request.workspace_dir).expanduser() if request.workspace_dir else Path.cwd()
    if not cwd.exists() or not cwd.is_dir():
        raise ValueError(f"workspaceDir does not exist or is not a directory: {cwd}")

    env = os.environ.copy()
    env.update(request.extra_env)
    start = time.monotonic()
    argv = build_crabbox_command(request)

    output_parts: list[str] = []
    output_size = 0
    timing: dict[str, Any] | None = None

    try:
        process = subprocess.Popen(
            argv,
            cwd=str(cwd),
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
    except FileNotFoundError as exc:
        raise RuntimeError("crabbox binary was not found on PATH") from exc

    timed_out = False
    timeout_timer: threading.Timer | None = None

    def kill_on_timeout() -> None:
        nonlocal timed_out
        timed_out = True
        process.kill()

    if request.timeout_seconds is not None:
        timeout_timer = threading.Timer(request.timeout_seconds, kill_on_timeout)
        timeout_timer.daemon = True
        timeout_timer.start()

    try:
        assert process.stdout is not None
        for line in process.stdout:
            if on_line:
                on_line(line.rstrip("\n"))
            if output_size < output_limit:
                remaining = output_limit - output_size
                chunk = line[:remaining]
                output_parts.append(chunk)
                output_size += len(chunk)
            parsed = _try_parse_json_object(line)
            if parsed and "exitCode" in parsed:
                timing = parsed
        exit_code = process.wait()
    finally:
        if timeout_timer:
            timeout_timer.cancel()

    if timed_out:
        output_parts.append(f"\ncrabbox run timed out after {request.timeout_seconds}s\n")

    output = "".join(output_parts)
    status, reason = classify_crabbox_exit(exit_code, output)
    return {
        "status": status,
        "exitCode": exit_code,
        "reason": reason,
        "retryable": status == "FAILED",
        "command": redact_command(argv),
        "provider": request.provider,
        "durationSeconds": round(time.monotonic() - start, 3),
        "timing": timing,
        "output": output,
        "outputTail": tail_lines(output),
        "truncated": output_size >= output_limit,
    }


def classify_crabbox_exit(exit_code: int, output: str) -> tuple[str, str]:
    if exit_code == 0:
        return "COMPLETED", "crabbox command completed"

    lowered = output.lower()
    terminal_markers = [
        "unauthorized",
        "forbidden",
        "missing api key",
        "requires islo_api_key",
        "requires crabbox_islo_api_key",
        "not logged in",
        "unknown flag",
        "invalid provider",
        "invalid config",
        "usage:",
    ]
    retryable_markers = [
        "capacity",
        "rate limit",
        "429",
        "timeout",
        "temporarily",
        "provision",
        "readiness",
        "stream",
        "sync",
    ]

    if any(marker in lowered for marker in terminal_markers):
        return "FAILED_WITH_TERMINAL_ERROR", "crabbox configuration or authentication failed"
    if any(marker in lowered for marker in retryable_markers):
        return "FAILED", "crabbox infrastructure path failed and may be retryable"
    return "FAILED", "remote command failed"


def tail_lines(output: str, limit: int = DEFAULT_TAIL_LINES) -> str:
    lines = output.splitlines()
    return "\n".join(lines[-limit:])


def _try_parse_json_object(line: str) -> dict[str, Any] | None:
    text = line.strip()
    if not text.startswith("{") or not text.endswith("}"):
        return None
    try:
        value = json.loads(text)
    except json.JSONDecodeError:
        return None
    return value if isinstance(value, dict) else None


def redact_command(argv: list[str]) -> list[str]:
    redacted: list[str] = []
    redact_next = False
    secret_flags = {"--token", "--token-stdin", "--password", "--secret"}
    for part in argv:
        if redact_next:
            redacted.append("<redacted>")
            redact_next = False
            continue
        redacted.append(part)
        if part in secret_flags:
            redact_next = True
    return redacted
