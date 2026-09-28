#!/usr/bin/env python3
"""Deterministic checks for Workflows documentation structure and fixtures."""

from __future__ import annotations

import json
import re
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
LOCAL_LINK = re.compile(r"\[[^\]]+\]\((?!https?://|mailto:|#)([^) >]+)(?:#[^)]+)?\)")


class MkDocsLoader(yaml.SafeLoader):
    pass


MkDocsLoader.add_constructor(
    "!relative", lambda loader, node: loader.construct_scalar(node)
)
MkDocsLoader.add_multi_constructor(
    "tag:yaml.org,2002:python/name:",
    lambda loader, suffix, node: suffix,
)


def load_config() -> dict[str, object]:
    return yaml.load(
        (ROOT / "mkdocs.yml").read_text(encoding="utf-8"), Loader=MkDocsLoader
    )


def workflow_targets(nav: list[object]) -> list[str]:
    return section_targets(nav, "Workflows")


def section_targets(nav: list[object], section_name: str) -> list[str]:
    section = next(
        (
            item[section_name]
            for item in nav
            if isinstance(item, dict) and section_name in item
        ),
        None,
    )
    if section is None:
        raise AssertionError(f"missing {section_name} nav section")
    targets: list[str] = []

    def walk(value: object) -> None:
        if isinstance(value, str):
            targets.append(value)
        elif isinstance(value, list):
            for item in value:
                walk(item)
        elif isinstance(value, dict):
            for child in value.values():
                walk(child)

    walk(section)
    return targets


def check_nav() -> None:
    config = load_config()
    targets = workflow_targets(config["nav"])
    duplicates = sorted({target for target in targets if targets.count(target) > 1})
    if duplicates:
        raise AssertionError(f"duplicate targets inside Workflows nav: {duplicates}")
    missing = [target for target in targets if not (DOCS / target).is_file()]
    if missing:
        raise AssertionError(f"missing Workflows nav targets: {missing}")


def check_cookbook_nav() -> None:
    config = load_config()
    targets = section_targets(config["nav"], "Design Patterns")
    expected = [
        "devguide/cookbook/index.md",
        "devguide/cookbook/microservice-orchestration.md",
        "devguide/cookbook/dynamic-parallelism.md",
        "devguide/cookbook/wait-and-timers.md",
        "devguide/cookbook/task-timeouts-and-retries.md",
        "devguide/cookbook/saga-compensation.md",
        "devguide/cookbook/http-poll-long-running-job.md",
        "devguide/cookbook/workflow-scheduling.md",
        "devguide/cookbook/dynamic-workflows.md",
        "devguide/cookbook/event-driven.md",
    ]
    duplicates = sorted({target for target in targets if targets.count(target) > 1})
    if duplicates:
        raise AssertionError(f"duplicate targets inside Design Patterns nav: {duplicates}")
    missing = [target for target in targets if not (DOCS / target).is_file()]
    if missing:
        raise AssertionError(f"missing Design Patterns nav targets: {missing}")
    classic_targets = [target for target in targets if target.startswith("devguide/cookbook/")]
    if classic_targets != expected:
        raise AssertionError(
            "Design Patterns nav targets do not match canonical recipe order: "
            f"expected {expected}, got {classic_targets}"
        )


def check_compatibility_routes() -> None:
    required = [
        DOCS / "quickstart/index.md",
        DOCS / "quickstart/first-workflow.md",
        DOCS / "devguide/how-tos/event-bus.md",
    ]
    missing = [str(path.relative_to(ROOT)) for path in required if not path.is_file()]
    if missing:
        raise AssertionError(f"missing compatibility route(s): {missing}")


def check_local_links() -> None:
    failures: list[str] = []
    targets = set(workflow_targets(load_config()["nav"]))
    targets.update({"quickstart/index.md"})
    for relative in targets:
        page = DOCS / relative
        for match in LOCAL_LINK.finditer(page.read_text(encoding="utf-8")):
            raw = match.group(1)
            raw = raw.split("#", 1)[0]
            if raw.startswith("{{") or raw.startswith("/"):
                continue
            target = (page.parent / raw).resolve()
            if target.suffix == "":
                continue
            if not target.exists():
                failures.append(f"{page.relative_to(ROOT)} -> {raw}")
    if failures:
        raise AssertionError("broken local links:\n" + "\n".join(failures))


def check_json_fixtures() -> None:
    fixtures = list((ROOT / "scheduler/examples").glob("*.json"))
    fixtures += list((DOCS / "devguide/cookbook/examples/events").glob("*.json"))
    fixtures += [DOCS / "devguide/cookbook/examples/workflow-test.json"]
    for fixture in fixtures:
        json.loads(fixture.read_text(encoding="utf-8"))


def main() -> None:
    check_nav()
    check_cookbook_nav()
    check_compatibility_routes()
    check_local_links()
    check_json_fixtures()
    print("Workflows and Cookbook documentation checks passed")


if __name__ == "__main__":
    main()
