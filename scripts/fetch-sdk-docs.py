#!/usr/bin/env python3
"""
Fetch SDK README files from GitHub and write them into docs/documentation/clientsdks/.

Run as part of the docs build or manually:
    python3 scripts/fetch-sdk-docs.py

Each SDK gets a markdown file with:
  - Front matter (description)
  - The README content with relative paths rewritten to absolute GitHub URLs
  - An examples table at the bottom (if the repo has an examples directory)
"""

import json
import os
import re
import sys
import urllib.request
import urllib.error

GITHUB_ORG = "conductor-oss"
GITHUB_API = "https://api.github.com"
GITHUB_RAW = "https://raw.githubusercontent.com"
GITHUB_BLOB = "https://github.com"

DOCS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                        "docs", "documentation", "clientsdks")

# SDK definitions: (repo, output_filename, display_name, examples_dir, description)
SDKS = [
    ("java-sdk",       "java-sdk.md",    "Java",              "examples",         "Build Conductor workers in Java with automated polling, thread management, and Spring Boot integration."),
    ("python-sdk",     "python-sdk.md",  "Python",            "examples",         "Build Conductor workers in Python with decorator-based task definitions, async support, and workflow management."),
    ("go-sdk",         "go-sdk.md",      "Go",                "examples",         "Build Conductor workers in Go with type-safe task definitions and workflow management."),
    ("javascript-sdk", "js-sdk.md",      "JavaScript",        "examples",         "Build Conductor workers in JavaScript/TypeScript with workflow management and task polling."),
    ("csharp-sdk",     "csharp-sdk.md",  "C#",                "csharp-examples",  "Build Conductor workers in C#/.NET with dependency injection, workflow management, and task polling."),
    ("ruby-sdk",        "ruby-sdk.md",   "Ruby",              "examples",         "Build Conductor workers in Ruby with idiomatic task definitions and workflow management."),
    ("rust-sdk",       "rust-sdk.md",    "Rust",              "examples",         "Build Conductor workers in Rust with type-safe task definitions and async workflow management."),
]


def github_get(url):
    """Fetch a URL from GitHub API or raw content."""
    req = urllib.request.Request(url)
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if not token:
        # Try gh CLI as fallback
        try:
            import subprocess
            token = subprocess.check_output(["gh", "auth", "token"], stderr=subprocess.DEVNULL).decode().strip()
        except Exception:
            pass
    if token:
        req.add_header("Authorization", f"token {token}")
    req.add_header("Accept", "application/vnd.github.v3+json")
    req.add_header("User-Agent", "conductor-docs-builder")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        print(f"  WARNING: HTTP {e.code} fetching {url}", file=sys.stderr)
        return None
    except Exception as e:
        print(f"  WARNING: {e} fetching {url}", file=sys.stderr)
        return None


def fetch_readme(repo):
    """Fetch the raw README.md from a GitHub repo."""
    url = f"{GITHUB_RAW}/{GITHUB_ORG}/{repo}/main/README.md"
    content = github_get(url)
    if content is None:
        # Try master branch
        url = f"{GITHUB_RAW}/{GITHUB_ORG}/{repo}/master/README.md"
        content = github_get(url)
    return content


def rewrite_paths(content, repo):
    """Rewrite relative paths in markdown to point to GitHub."""
    base_raw = f"{GITHUB_RAW}/{GITHUB_ORG}/{repo}/main"
    base_blob = f"{GITHUB_BLOB}/{GITHUB_ORG}/{repo}/blob/main"

    # Rewrite image references: ![alt](relative/path.png)
    # Don't touch absolute URLs (http://, https://)
    content = re.sub(
        r'(!\[[^\]]*\]\()(?!https?://|//)([^)]+)(\))',
        lambda m: f'{m.group(1)}{base_raw}/{m.group(2)}{m.group(3)}',
        content
    )

    # Rewrite link references: [text](relative/path) but not anchors (#) or absolute URLs
    content = re.sub(
        r'(\[[^\]]*\]\()(?!https?://|//|#)([^)]+)(\))',
        lambda m: f'{m.group(1)}{base_blob}/{m.group(2)}{m.group(3)}',
        content
    )

    # Rewrite nested badge links: [![...](badge-url)](relative-path)
    # The main regex can't handle nested brackets, so handle this separately
    content = re.sub(
        r'(\]\()(?!https?://|//|#)([^)]+)(\))\s*$',
        lambda m: f'{m.group(1)}{base_blob}/{m.group(2)}{m.group(3)}',
        content,
        flags=re.MULTILINE
    )

    # Rewrite HTML img src attributes
    content = re.sub(
        r'(<img[^>]+src=")(?!https?://|//)([^"]+)(")',
        lambda m: f'{m.group(1)}{base_raw}/{m.group(2)}{m.group(3)}',
        content
    )

    return content


def strip_title(content):
    """Remove the first H1 heading (# Title) since we add our own."""
    lines = content.split('\n')
    for i, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith('# ') and not stripped.startswith('## '):
            lines.pop(i)
            # Also remove blank line after title if present
            if i < len(lines) and lines[i].strip() == '':
                lines.pop(i)
            break
    return '\n'.join(lines)


def strip_preamble(content):
    """Remove badges, intro blurb, and star-request that appear before the first ## heading."""
    lines = content.split('\n')
    first_h2 = None
    for i, line in enumerate(lines):
        if line.strip().startswith('## '):
            first_h2 = i
            break
    if first_h2 is None:
        return content
    # Keep everything from the first ## heading onward
    return '\n'.join(lines[first_h2:])


def strip_toc(content):
    """Remove HTML comment TOC blocks (<!-- TOC --> ... <!-- TOC -->) from README."""
    content = re.sub(r'<!-- TOC -->.*?<!-- TOC -->', '', content, flags=re.DOTALL)
    # Also remove markdown-style TOC (indented list of anchor links at the top)
    # These are blocks of lines that are all "  * [text](#anchor)" or "- [text](#anchor)"
    lines = content.split('\n')
    result = []
    in_toc = False
    for line in lines:
        stripped = line.strip()
        if stripped and re.match(r'^[-*]\s+\[.*\]\(#', stripped):
            in_toc = True
            continue
        if in_toc and stripped == '':
            in_toc = False
            continue
        if in_toc and re.match(r'^[-*]\s+\[.*\]\(#', stripped):
            continue
        in_toc = False
        result.append(line)
    return '\n'.join(result)


def fetch_examples(repo, examples_dir):
    """Fetch the list of examples from a repo's examples directory."""
    if not examples_dir:
        return []

    url = f"{GITHUB_API}/repos/{GITHUB_ORG}/{repo}/contents/{examples_dir}"
    raw = github_get(url)
    if raw is None:
        return []

    try:
        items = json.loads(raw)
    except json.JSONDecodeError:
        return []

    if not isinstance(items, list):
        return []

    examples = []
    for item in sorted(items, key=lambda x: x.get("name", "")):
        name = item.get("name", "")
        item_type = item.get("type", "")
        html_url = item.get("html_url", "")

        # Skip non-interesting files
        if name.startswith(".") or name.startswith("__"):
            continue
        if name in ("go.mod", "go.sum", "build.gradle", "settings.gradle",
                     "pom.xml", "Cargo.toml", "package.json", "tsconfig.json",
                     "Dockerfile", "DockerfileMacArm", ".gitignore",
                     "csharp-examples.csproj"):
            continue
        if name == "Properties":
            continue

        # Format display name from filename
        display = name
        if item_type == "file":
            # Remove extension for display
            display = os.path.splitext(name)[0]
            # Convert snake_case/kebab-case to readable
            display = display.replace("_", " ").replace("-", " ").title()

        if item_type == "dir":
            display = name.replace("_", " ").replace("-", " ").title()

        examples.append((display, html_url, item_type))

    return examples


def build_examples_table(repo, examples_dir):
    """Build a markdown table of examples."""
    examples = fetch_examples(repo, examples_dir)
    if not examples:
        return ""

    base_url = f"{GITHUB_BLOB}/{GITHUB_ORG}/{repo}/tree/main/{examples_dir}"
    lines = [
        "",
        "## Examples",
        "",
        f"Browse all examples on GitHub: [{GITHUB_ORG}/{repo}/{examples_dir}]({base_url})",
        "",
        "| Example | Type |",
        "|---|---|",
    ]

    for display, url, item_type in examples:
        icon = "directory" if item_type == "dir" else "file"
        lines.append(f"| [{display}]({url}) | {icon} |")

    lines.append("")
    return "\n".join(lines)


def process_sdk(repo, output_file, display_name, examples_dir, description):
    """Fetch README, process it, and write the output file."""
    print(f"Fetching {display_name} SDK from {GITHUB_ORG}/{repo}...")

    readme = fetch_readme(repo)
    if readme is None:
        print(f"  SKIP: Could not fetch README for {repo}", file=sys.stderr)
        return False

    # Process content
    readme = strip_title(readme)
    readme = strip_toc(readme)
    readme = strip_preamble(readme)
    readme = rewrite_paths(readme, repo)

    # Build examples section
    examples_section = build_examples_table(repo, examples_dir)

    # Compose final markdown
    repo_url = f"{GITHUB_BLOB}/{GITHUB_ORG}/{repo}"
    output = f"""---
description: "{description}"
---

# {display_name} SDK

!!! info "Source"
    GitHub: [{GITHUB_ORG}/{repo}]({repo_url}) | Report issues and contribute on GitHub.

{readme}
{examples_section}"""

    # Write output
    output_path = os.path.join(DOCS_DIR, output_file)
    with open(output_path, "w") as f:
        f.write(output)

    print(f"  Wrote {output_path}")
    return True


def main():
    os.makedirs(DOCS_DIR, exist_ok=True)

    success = 0
    for repo, output_file, display_name, examples_dir, description in SDKS:
        if process_sdk(repo, output_file, display_name, examples_dir, description):
            success += 1

    print(f"\nDone: {success}/{len(SDKS)} SDK docs generated.")

    if success < len(SDKS):
        print("Some SDKs could not be fetched. Check warnings above.", file=sys.stderr)


if __name__ == "__main__":
    main()
