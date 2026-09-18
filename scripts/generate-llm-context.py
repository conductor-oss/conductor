#!/usr/bin/env python3
"""Generate the curated, source-backed llms-full.txt documentation context."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
MANIFEST = DOCS / "llms-manifest.txt"
OUTPUT = DOCS / "llms-full.txt"
FRONT_MATTER = re.compile(r"\A---\n.*?\n---\n", re.DOTALL)
SNIPPET = re.compile(r'^(?P<indent>[ \t]*)--8<--\s+"(?P<path>[^"]+)"\s*$', re.MULTILINE)


def source_paths() -> list[Path]:
    paths: list[Path] = []
    for raw_line in MANIFEST.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        path = DOCS / line
        if not path.is_file():
            raise FileNotFoundError(f"LLM context source is missing: {line}")
        paths.append(path)
    return paths


def expand_snippets(
    content: str,
    source: Path,
    *,
    root: Path = ROOT,
    stack: tuple[Path, ...] = (),
) -> str:
    """Expand repository-root snippet includes with traversal and cycle protection."""

    root = root.resolve()
    source = source.resolve()
    chain = (*stack, source)

    def replace(match: re.Match[str]) -> str:
        requested = Path(match.group("path"))
        if requested.is_absolute():
            raise ValueError(f"absolute snippet path is not allowed in {source}: {requested}")
        included = (root / requested).resolve()
        try:
            included.relative_to(root)
        except ValueError as exc:
            raise ValueError(f"snippet escapes repository root in {source}: {requested}") from exc
        if included in chain:
            cycle = " -> ".join(str(path) for path in (*chain, included))
            raise ValueError(f"cyclic snippet include: {cycle}")
        if not included.is_file():
            raise FileNotFoundError(f"snippet included by {source} is missing: {requested}")
        expanded = expand_snippets(
            included.read_text(encoding="utf-8"),
            included,
            root=root,
            stack=chain,
        ).rstrip("\n")
        indent = match.group("indent")
        return "\n".join(f"{indent}{line}" if line else "" for line in expanded.splitlines())

    return SNIPPET.sub(replace, content)


def render() -> str:
    parts = [
        "# Conductor LLM context\n",
        "This generated file is a curated technical context for Conductor. "
        "Source pages remain authoritative; regenerate this file with "
        "scripts/generate-llm-context.py after updating a listed page.\n",
    ]
    for path in source_paths():
        relative = path.relative_to(DOCS)
        content = FRONT_MATTER.sub("", path.read_text(encoding="utf-8"))
        content = expand_snippets(content, path)
        content = "\n".join(
            line.expandtabs(4).rstrip() for line in content.splitlines()
        ).strip()
        parts.extend([f"\n\n<!-- Source: {relative} -->\n", content, "\n"])
    return "".join(parts)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail when llms-full.txt is stale")
    args = parser.parse_args()
    generated = render()
    if args.check:
        if not OUTPUT.is_file() or OUTPUT.read_text(encoding="utf-8") != generated:
            print("llms-full.txt is stale; run python3 scripts/generate-llm-context.py")
            return 1
        return 0
    OUTPUT.write_text(generated, encoding="utf-8")
    print(f"wrote {OUTPUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
