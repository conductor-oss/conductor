"""Deploy cookbook agents backed by the local MCP Testkit server.

This module is deliberately a local-demo configuration: every agent can discover
the complete Testkit catalog at ``http://127.0.0.1:3001/mcp``.  Production
deployments must use a scoped MCP allowlist and a per-tool policy; never expose
an entire tool catalog to an agent simply because it is convenient for a demo.

Run ``python deploy_local_cookbook_agents.py deploy`` once, then keep
``python deploy_local_cookbook_agents.py serve`` running while the parent
workflow executions invoke the deployed agents.
"""

from __future__ import annotations

import json
import sys

from conductor.ai.agents import (
    Agent,
    AgentRuntime,
    OnFail,
    Position,
    RegexGuardrail,
    mcp_tool,
    tool,
)
from google.adk.agents import Agent as AdkAgent
from google.adk.tools.mcp_tool import McpToolset, StreamableHTTPConnectionParams
from langchain.agents import create_agent
from langchain_core.tools import tool as langchain_tool
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client


MCP_TESTKIT_URL = "http://127.0.0.1:3001/mcp"

# The guard is enforced before the approval boundary.  It prevents a prompt or
# tool argument from turning the local demo notification into a PII exfiltration
# example, while approval_required preserves a durable operator decision point.
no_payment_card_data = RegexGuardrail(
    patterns=[r"\b(?:\d[ -]?){15}\d\b"],
    name="no_payment_card_data",
    position=Position.INPUT,
    on_fail=OnFail.RAISE,
    message="Refusing to send payment-card-shaped data to an external action.",
)


@tool(guardrails=[no_payment_card_data], approval_required=True)
def request_notification(destination: str, summary: str) -> dict[str, str]:
    """Request an approved notification; replace with an idempotent integration."""
    return {"status": "approved-notification-requested", "destination": destination}


def testkit_catalog():
    """Return the full local Testkit catalog for one independently deployed agent."""
    return mcp_tool(MCP_TESTKIT_URL)


async def _testkit_request(method: str, arguments: dict[str, object] | None = None) -> str:
    """Open a short-lived local MCP session for a LangChain tool call."""
    async with streamablehttp_client(MCP_TESTKIT_URL) as (read, write, _):
        async with ClientSession(read, write) as session:
            await session.initialize()
            if method == "list":
                return json.dumps([tool.name for tool in (await session.list_tools()).tools])
            result = await session.call_tool(method, arguments or {})
            return result.model_dump_json()


@langchain_tool
def list_mcp_testkit_tools() -> str:
    """Discover the full local MCP Testkit catalog."""
    import anyio

    return anyio.run(_testkit_request, "list")


@langchain_tool
def call_mcp_testkit_tool(method: str, arguments_json: str = "{}") -> str:
    """Call any discovered local MCP Testkit tool with a JSON arguments object."""
    import anyio

    return anyio.run(_testkit_request, method, json.loads(arguments_json))


guarded_incident_planner = Agent(
    name="guarded-incident-planner",
    model="openai/gpt-4o",
    instructions=(
        "Use the MCP Testkit tools to gather incident evidence and summarize it. "
        "Use request_notification only when a human approves the durable tool gate."
    ),
    tools=[testkit_catalog(), request_notification],
)

langchain_entitlement_investigator = create_agent(
    "openai:gpt-4o",
    tools=[list_mcp_testkit_tools, call_mcp_testkit_tool],
    system_prompt=(
        "Use the available MCP Testkit tools to investigate the question. "
        "Return the evidence used and do not attempt external writes."
    ),
    name="langchain-entitlement-investigator",
)

adk_order_exception_triage = AdkAgent(
    name="adk_order_exception_triage",
    model="openai/gpt-4o",
    instruction=(
        "Use the available MCP Testkit tools to investigate an order exception, "
        "then recommend a disposition. Do not perform a refund or fulfillment action."
    ),
    tools=[
        McpToolset(
            connection_params=StreamableHTTPConnectionParams(url=MCP_TESTKIT_URL)
        )
    ],
)

security_reviewer = Agent(
    name="security-reviewer",
    model="openai/gpt-4o",
    instructions="Use MCP Testkit evidence to identify security risks; return concise findings.",
    tools=[testkit_catalog()],
)

reliability_reviewer = Agent(
    name="reliability-reviewer",
    model="openai/gpt-4o",
    instructions="Use MCP Testkit evidence to identify reliability risks; return concise findings.",
    tools=[testkit_catalog()],
)

AGENTS = (
    guarded_incident_planner,
    langchain_entitlement_investigator,
    adk_order_exception_triage,
    security_reviewer,
    reliability_reviewer,
)


def main() -> None:
    action = sys.argv[1] if len(sys.argv) == 2 else ""
    if action not in {"deploy", "serve"}:
        raise SystemExit("Usage: deploy_local_cookbook_agents.py {deploy|serve}")

    with AgentRuntime() as runtime:
        if action == "deploy":
            for deployment in runtime.deploy(*AGENTS):
                print(deployment.registered_name)
        else:
            runtime.serve(*AGENTS)


if __name__ == "__main__":
    main()
