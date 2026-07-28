#!/usr/bin/env python3
"""Ensure copied AI cookbook assets still match their production examples.

Cookbook assets may rename a workflow, annotate its description, or substitute a
stable local deployed-agent name.  Every other graph change must be made first
in ``ai/examples`` and then copied here intentionally.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
EXAMPLES = ROOT / "ai/examples"
ASSETS = ROOT / "docs/devguide/ai/cookbook/assets"

COPIES = {
    "rag-assistant.json": ("07-rag-complete.json", {}),
    "mcp-incident-investigator.json": (
        "10-mcp-ai-agent.json",
        {
            "http://localhost:3001/mcp": "http://127.0.0.1:3001/mcp",
            "anthropic": "openai",
            "claude-3-5-sonnet-20241022": "gpt-4o",
        },
    ),
    "coding-review-agent.json": ("19-coding-agent.json", {}),
    "web-research-brief.json": (
        "21-web-search-research-agent.json",
        {"anthropic": "openai", "claude-sonnet-4-20250514": "gpt-4o"},
    ),
    "reusable-conductor-agent.json": (
        "31-conductor-agent-basic.json",
        {"planner": "guarded-incident-planner"},
    ),
    "human-approved-action.json": (
        "32-conductor-agent-human-in-loop.json",
        {"planner": "guarded-incident-planner"},
    ),
    "parallel-specialist-review.json": (
        "33-conductor-agent-multi-agent.json",
        {
            "run_planner": "run_security_reviewer",
            "run_planner_ref": "run_security_reviewer_ref",
            "planner": "security-reviewer",
            "run_researcher": "run_reliability_reviewer",
            "run_researcher_ref": "run_reliability_reviewer_ref",
            "researcher": "reliability-reviewer",
        },
    ),
    "conductor-agent-cancellation.json": (
        "34-conductor-agent-cancel.json",
        {"planner": "guarded-incident-planner"},
    ),
    "governed-pr-reviewer.json": ("35-governed-adaptive-agent.json", {}),
    "ai-workflow-routing.json": ("36-ai-workflow-routing.json", {}),
    "ai-route-support-ticket.json": ("36a-ai-route-support-ticket.json", {}),
    "ai-route-refund-request.json": ("36b-ai-route-refund-request.json", {}),
    "ai-route-sales-lead.json": ("36c-ai-route-sales-lead.json", {}),
}


def replace(value: object, replacements: dict[str, str]) -> object:
    if isinstance(value, dict):
        return {key: replace(item, replacements) for key, item in value.items()}
    if isinstance(value, list):
        return [replace(item, replacements) for item in value]
    if isinstance(value, str):
        return replacements.get(value, value)
    return value


def normalized(path: Path, replacements: dict[str, str]) -> dict[str, object]:
    workflow = json.loads(path.read_text(encoding="utf-8"))
    workflow = replace(workflow, replacements)
    assert isinstance(workflow, dict)
    workflow.pop("name", None)
    workflow.pop("description", None)
    return workflow


def main() -> None:
    for asset_name, (example_name, replacements) in COPIES.items():
        asset = ASSETS / asset_name
        example = EXAMPLES / example_name
        cookbook = json.loads(asset.read_text(encoding="utf-8"))
        description = cookbook.get("description", "")
        marker = f"Derived from ai/examples/{example_name}."
        if marker not in description:
            raise AssertionError(f"{asset_name} is missing its source marker")
        if normalized(asset, {}) != normalized(example, replacements):
            raise AssertionError(
                f"{asset_name} drifted from ai/examples/{example_name}; "
                "make the graph change upstream or record an intentional adaptation"
            )
    print("Cookbook AI assets match their ai/examples sources")


if __name__ == "__main__":
    main()
