#!/usr/bin/env python3
"""Render cookbook execution evidence directly from a Conductor server.

Set ``COOKBOOK_WORKFLOW_IDS`` to a comma-separated list of executions after
running the local matrix. The script deliberately reports terminal failures and
terminations instead of silently treating them as passing evidence.
"""

from __future__ import annotations

import json
import os
import sys
from urllib.request import Request, urlopen


def fetch(base_url: str, workflow_id: str) -> dict[str, object]:
    request = Request(f"{base_url.rstrip('/')}/workflow/{workflow_id}?includeTasks=true")
    token = os.environ.get("CONDUCTOR_AUTH_TOKEN")
    if token:
        request.add_header("X-Authorization", token)
    with urlopen(request) as response:  # noqa: S310 - the server is explicit user configuration
        return json.load(response)


def main() -> None:
    base_url = os.environ.get("CONDUCTOR_SERVER_URL", "http://localhost:8080/api")
    ids = [item for item in os.environ.get("COOKBOOK_WORKFLOW_IDS", "").split(",") if item]
    if not ids:
        raise SystemExit("Set COOKBOOK_WORKFLOW_IDS to one or more workflow execution IDs.")
    ui_base = base_url.removesuffix("/api")
    print("| Workflow ID | Local UI URL | Parent status | Agent execution ID(s) | MCP task reference(s) | Selected tool(s) | Terminal result |")
    print("|---|---|---|---|---|---|---|")
    for workflow_id in ids:
        workflow = fetch(base_url, workflow_id)
        tasks = workflow.get("tasks", [])
        agents = [task for task in tasks if task.get("taskType") == "AGENT"]
        mcp = [task for task in tasks if task.get("taskType") == "CALL_MCP_TOOL"]
        agent_ids = ", ".join(
            dict.fromkeys(
                str(task.get("outputData", {}).get("executionId", ""))
                for task in agents
                if task.get("outputData", {}).get("executionId")
            )
        )
        mcp_refs = ", ".join(str(task.get("referenceTaskName", "")) for task in mcp)
        tools = ", ".join(str(task.get("inputData", {}).get("method", "")) for task in mcp)
        status = str(workflow.get("status", "UNKNOWN"))
        has_policy_gate = any(task.get("taskType") == "HUMAN" for task in tasks) or any(
            task.get("outputData", {}).get("waiting") is True for task in agents
        )
        result = (
            "policy gate exercised; no write attempted"
            if status == "COMPLETED" and has_policy_gate and not mcp
            else status
        )
        print(f"| {workflow_id} | {ui_base}/execution/{workflow_id} | {status} | {agent_ids or '-'} | {mcp_refs or '-'} | {tools or '-'} | {result} |")


if __name__ == "__main__":
    main()
