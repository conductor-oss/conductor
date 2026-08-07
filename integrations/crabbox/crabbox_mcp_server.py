#!/usr/bin/env python3
"""MCP server exposing Crabbox-backed execution tools to Conductor."""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
from typing import Any

from crabbox_bridge import CrabboxRequest, run_crabbox

try:
    from mcp.server.fastmcp import FastMCP
except ImportError as exc:  # pragma: no cover - startup guidance
    raise SystemExit(
        "Missing MCP Python SDK. Install it with: pip install -r integrations/crabbox/requirements.txt"
    ) from exc


mcp = FastMCP("conductor-crabbox", stateless_http=True, json_response=True)


@mcp.tool()
def crabbox_run_command(
    command: str,
    provider: str = "local-container",
    shell: bool = True,
    workspace_dir: str | None = None,
    profile: str | None = None,
    timeout_seconds: int | None = None,
    keep_on_failure: bool = False,
) -> dict[str, Any]:
    """Run a command through Crabbox and return status, output tail, and timing."""

    request = CrabboxRequest(
        command=command,
        provider=provider,
        shell=shell,
        workspace_dir=workspace_dir,
        profile=profile,
        timeout_seconds=timeout_seconds,
        keep_on_failure=keep_on_failure,
    )
    return run_crabbox(request)


@mcp.tool()
def crabbox_doctor(provider: str = "local-container") -> dict[str, Any]:
    """Run `crabbox doctor` for the configured provider."""

    if not shutil.which("crabbox"):
        return {
            "status": "FAILED_WITH_TERMINAL_ERROR",
            "reason": "crabbox binary was not found on PATH",
            "exitCode": 127,
        }

    result = subprocess.run(
        ["crabbox", "doctor", "--provider", provider],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    return {
        "status": "COMPLETED" if result.returncode == 0 else "FAILED",
        "exitCode": result.returncode,
        "provider": provider,
        "output": result.stdout,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Serve Crabbox tools over MCP.")
    parser.add_argument("--transport", default=os.getenv("MCP_TRANSPORT", "streamable-http"))
    parser.add_argument("--host", default=os.getenv("MCP_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MCP_PORT", "3001")))
    args = parser.parse_args()

    if args.transport == "streamable-http":
        mcp.run(transport="streamable-http", host=args.host, port=args.port)
    else:
        mcp.run(transport=args.transport)


if __name__ == "__main__":
    main()
