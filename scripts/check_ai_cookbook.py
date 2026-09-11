#!/usr/bin/env python3
"""Validate the AI Cookbook's compact, non-duplicated navigation contract."""

from __future__ import annotations

import re
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"

# The compact-title contract applies to the cookbook's own pages.  General
# cookbook pages cross-listed into this section keep their descriptive titles.
COMPACT_TITLE_PREFIX = "devguide/ai/cookbook/"
MAX_TITLE_WORDS = 4


class MkDocsLoader(yaml.SafeLoader):
    pass


MkDocsLoader.add_constructor(
    "!relative", lambda loader, node: loader.construct_scalar(node)
)
MkDocsLoader.add_multi_constructor(
    "tag:yaml.org,2002:python/name:",
    lambda loader, suffix, node: suffix,
)


def ai_cookbook_targets() -> list[str]:
    config = yaml.load(
        (ROOT / "mkdocs.yml").read_text(encoding="utf-8"), Loader=MkDocsLoader
    )
    section = next(item["AI Cookbook"] for item in config["nav"] if "AI Cookbook" in item)
    targets: list[str] = []

    def visit(value: object) -> None:
        if isinstance(value, str):
            targets.append(value)
        elif isinstance(value, list):
            for item in value:
                visit(item)
        elif isinstance(value, dict):
            for item in value.values():
                visit(item)

    visit(section)
    return targets


def main() -> None:
    targets = ai_cookbook_targets()
    if len(targets) != len(set(targets)):
        raise AssertionError("AI Cookbook navigation contains duplicate pages")
    for target in targets:
        page = DOCS / target
        if not page.is_file():
            raise AssertionError(f"AI Cookbook page is missing: {target}")
        heading = re.search(r"(?m)^# (.+)$", page.read_text(encoding="utf-8"))
        if heading is None:
            raise AssertionError(f"AI Cookbook page has no H1: {target}")
        if not target.startswith(COMPACT_TITLE_PREFIX):
            continue
        if len(heading.group(1).split()) > MAX_TITLE_WORDS:
            raise AssertionError(
                f"AI Cookbook title exceeds {MAX_TITLE_WORDS} words: {target}"
            )
    print("AI Cookbook navigation and compact titles are valid")


if __name__ == "__main__":
    main()
