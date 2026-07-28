import html
import os
import re


# Single source of truth for diagram styling. This is prepended to every
# Mermaid fence, so diagrams must NOT carry their own `---config---`
# front matter: Mermaid only accepts front matter as the very first thing in
# the diagram, and this directive already occupies that position.
#
# `look: handDrawn` needs Mermaid >= 11 (bundled by mkdocs-material >= 9.6).
# `clusterBkg`/`clusterBorder` override Mermaid's hardcoded #ffffde subgraph
# yellow, which no other theme variable reaches.
MERMAID_WORKFLOW_INIT = """%%{init: {'look': 'handDrawn', 'theme': 'base', 'themeVariables': {'primaryColor': '#eef2ff', 'primaryBorderColor': '#1e40af', 'primaryTextColor': '#1e293b', 'lineColor': '#1e3a8a', 'edgeLabelBackground': '#ffffff', 'clusterBkg': '#fbfcff', 'clusterBorder': '#2563eb', 'fontFamily': 'Patrick Hand, Segoe Print, cursive', 'fontSize': '16px'}, 'flowchart': {'nodeSpacing': 50, 'rankSpacing': 58, 'padding': 14, 'htmlLabels': true, 'curve': 'basis'}}}%%
"""


def mermaid_fence(source, language, class_name, options, md, **kwargs):
    """Render Mermaid diagrams in a compact, consistently framed workflow card."""
    from pymdownx.superfences import fence_code_format

    rendered = fence_code_format(
        MERMAID_WORKFLOW_INIT + source,
        language,
        class_name,
        options,
        md,
        **kwargs,
    )
    return rendered.replace(
        '<pre class="mermaid">',
        '<div class="workflow-diagram"><pre class="mermaid">',
        1,
    ).replace("</pre>", "</pre></div>", 1)


VISUAL_JOURNEY_PAGES = {
    "quickstart/index.md",
    "devguide/ai/a2a-integration.md",
    "devguide/ai/agent-framework-recipes.md",
    "devguide/ai/conductor-agents.md",
    "devguide/ai/first-ai-agent.md",
    "devguide/ai/human-in-the-loop.md",
    "devguide/ai/mcp-guide.md",
}

SDK_PAGE_CONFIG = {
    "java": {
        "name": "Java",
        "examples": "https://github.com/conductor-oss/java-sdk/tree/main/examples",
        "agentic_examples": "https://github.com/conductor-oss/java-sdk/tree/main/agent-examples",
        "api_examples": None,
        "agent": True,
    },
    "python": {
        "name": "Python",
        "examples": "https://github.com/conductor-oss/python-sdk/tree/main/examples",
        "agentic_examples": "https://github.com/conductor-oss/python-sdk/tree/main/examples/agentic_workflows",
        "api_examples": None,
        "agent": True,
    },
    "go": {
        "name": "Go",
        "examples": "https://github.com/conductor-oss/go-sdk/tree/main/examples",
        "agentic_examples": None,
        "api_examples": None,
        "agent": False,
    },
    "javascript": {
        "name": "JavaScript / TypeScript",
        "examples": "https://github.com/conductor-oss/javascript-sdk/tree/main/examples",
        "agentic_examples": "https://github.com/conductor-oss/javascript-sdk/tree/main/examples/agentic-workflows",
        "api_examples": "https://github.com/conductor-oss/javascript-sdk/tree/main/examples/api-journeys",
        "agent": True,
    },
    "csharp": {
        "name": "C# / .NET",
        "examples": "https://github.com/conductor-oss/csharp-sdk/tree/main/csharp-examples",
        "agentic_examples": None,
        "api_examples": None,
        "agent": True,
    },
    "ruby": {
        "name": "Ruby",
        "examples": "https://github.com/conductor-oss/ruby-sdk/tree/main/examples",
        "agentic_examples": "https://github.com/conductor-oss/ruby-sdk/tree/main/examples/agentic_workflows",
        "api_examples": None,
        "agent": False,
    },
    "rust": {
        "name": "Rust",
        "examples": "https://github.com/conductor-oss/rust-sdk/tree/main/examples",
        "agentic_examples": "https://github.com/conductor-oss/rust-sdk/tree/main/examples",
        "api_examples": None,
        "agent": False,
    },
}


def sdk_intro(language):
    """Return the shared navigation and connection content for an SDK page."""
    config = SDK_PAGE_CONFIG[language]
    agent_link = "[Run your first agent](../../quickstart/first-agent.md)" if config["agent"] else "Coming soon"

    def example_link(url, label):
        return f"[{label}]({url})" if url else "Not currently maintained upstream"

    csharp_note = "\n\nC# reads `CONDUCTOR_SERVER_URL` when you set `Configuration.BasePath`; pass `OrkesAuthenticationSettings` explicitly for key/secret authentication."
    connection_note = csharp_note if language == "csharp" else "\n\nThis SDK reads these environment variables when constructing its standard client configuration."

    return f'''## Start here

| Goal | Guide |
|---|---|
| Run a workflow | [Run your first workflow](../../quickstart/first-workflow.md) |
| Write a worker | [Write your first worker](../../quickstart/first-worker.md) |
| Build an agent | {agent_link} |

## Featured examples

| Category | Maintained upstream example |
|---|---|
| Workflow and worker | [Examples]({config["examples"]}) |
| Agentic workflow | {example_link(config["agentic_examples"], "Agentic workflow examples")} |
| API journey | {example_link(config["api_examples"], "API journey examples")} |

The agentic-workflow row covers SDK examples that orchestrate LLMs or tools. It is separate from the SDK-authored Conductor Agent quickstart, which is {"available above" if config["agent"] else "coming soon for this SDK"}.

!!! info "Connect to Conductor"
    For local OSS, set `CONDUCTOR_SERVER_URL=http://localhost:8080/api`.

    For Orkes Developer Edition, set `CONDUCTOR_SERVER_URL=https://developer.orkescloud.com/api`, `CONDUCTOR_AUTH_KEY`, and `CONDUCTOR_AUTH_SECRET`. Keep credentials out of source control.{connection_note}
'''


def on_pre_page_macros(env):
    """Place each compact page description directly after its H1.

    Dedicated tutorial journeys already provide a richer visual introduction.
    For the remaining public pages, reuse front-matter descriptions and remove
    an identical first paragraph so the summary is additive only when needed.
    """
    page = env.page
    redirect_target = (page.meta or {}).get("redirect_to")
    if redirect_target:
        site_url = env.variables["config"]["site_url"].rstrip("/")
        page.canonical_url = f"{site_url}/{redirect_target.lstrip('/')}"

    meta = page.meta or {}
    description = meta.get("description")
    has_visual_hero = re.search(
        r'<section class="(?:concept-hero|integration-hero|framework-hero|agent-runtime-hero|agent-concepts-hero)',
        env.markdown,
    )
    # AI Cookbook recipes lead with their workflow diagram as the visual
    # headline, so the summary card would push it below the fold. The
    # front-matter description is still used for meta/og tags.
    is_cookbook_recipe = page.file.src_path.startswith("devguide/ai/cookbook/")
    if (
        not description
        or page.file.src_path in VISUAL_JOURNEY_PAGES
        or has_visual_hero
        or is_cookbook_recipe
    ):
        return

    heading = re.search(r"(?m)^# [^\n]+\n", env.markdown)
    if not heading:
        return

    remaining = env.markdown[heading.end() :].lstrip("\n")
    first_paragraph = re.match(r"(.+?)(?:\n\s*\n|$)", remaining, re.DOTALL)
    summary_text = description
    if first_paragraph:
        candidate = " ".join(first_paragraph.group(1).split())
        # Move only plain prose into the card. Rich Markdown stays in the
        # document and the front-matter description remains the summary.
        if candidate and not re.search(r"[`*_\[\]<>|]|^#{1,6} |^!!!|^```", candidate):
            summary_text = candidate
            remaining = remaining[first_paragraph.end() :].lstrip("\n")
        elif remaining.startswith(description):
            remaining = remaining[len(description) :].lstrip("\n")

    source_repo = meta.get("source_repo")
    source = ""
    if source_repo:
        source = f'    <p class="page-summary__source">Source: <a href="{html.escape(source_repo, quote=True)}">{html.escape(source_repo.removeprefix("https://github.com/"))}</a></p>\n'
    summary = (
        '<aside class="page-summary" aria-label="Page summary">\n'
        "  <div>\n"
        f'    <p class="page-summary__text">{html.escape(summary_text)}</p>\n'
        f"{source}"
        "  </div>\n"
        "</aside>"
    )
    if meta.get("sdk_page"):
        remaining = re.sub(r'^!!! info "Source"\n(?:    .*\n?)+\n?', "", remaining)
        remaining = re.sub(r'^## Connect to Conductor\n.*?(?=^## |\Z)', "", remaining, flags=re.DOTALL | re.MULTILINE)
        remaining = re.sub(r'^## (Frequently Asked Questions|FAQ)\n.*?(?=^## |\Z)', "", remaining, flags=re.DOTALL | re.MULTILINE)
        # The former README import appended a generated directory listing after
        # the authored examples catalog. The shared featured table is the
        # primary path; retain the authored catalog but omit that duplicate.
        remaining = re.sub(r'^## Examples\n\nBrowse all examples on GitHub:.*\Z', "", remaining, flags=re.DOTALL | re.MULTILINE)
        remaining = sdk_intro(meta["sdk_page"]) + "\n" + remaining.lstrip("\n")
    env.markdown = env.markdown[: heading.end()] + "\n" + summary + "\n\n" + remaining

def define_env(env):
    "Hook function"

    @env.macro
    def insert_content(key = None):
        key = key or env.page.title
        filename = env.variables['extra']['additional_content'][key]
        return include_file(filename)

    @env.macro
    def include_file(filename):
        prefix = env.variables['config']['docs_dir']
        full_filename = os.path.join(prefix, filename)
        with open(full_filename, 'r') as f:
            lines = f.readlines()
        return ''.join(lines)


    """ 
    def copy_markdown_images(tmpRoot, markdown):
        # root = os.path.dirname(os.path.dirname(self.page.url))
        root = self.page.url

        paths = []

        p = re.compile("!\[.*\]\((.*)\)")
        it = p.finditer(markdown)
        for match in it:
            path = match.group(1)
            paths.append(path)

            destinationPath = os.path.realpath(self.config['base_path'] + "/" +
                                               root + "/gen_/" + path)

            if not os.path.isfile(destinationPath):
                print("Copying image: " + path + " to " + destinationPath)

                os.makedirs(os.path.dirname(destinationPath), exist_ok=True)
                shutil.copyfile(tmpRoot + "/" + path, destinationPath)

        for path in paths:
            markdown = markdown.replace(path, "gen_/" + path)

        return markdown 
    """

    @env.macro
    def snippet(file_path, section_name, num_sections=1):
        p = re.compile("^#+ ")
        m = p.search(section_name)
        if m:
            section_level = m.span()[1] - 1
            root = env.variables['config']['docs_dir']
            full_path = os.path.join(root, file_path)

            content = ""
            with open(full_path, 'r') as myfile:
                content = myfile.read()

            p = re.compile("^" + section_name + "$", re.MULTILINE)
            start = p.search(content)
            start_span = start.span()
            p = re.compile("^#{1," + str(section_level) + "} ", re.MULTILINE)

            result = ""            
            all = [x for x in p.finditer(content[start_span[1]:])]
            
            print (len(all))

            if len(all) == 0 or (num_sections-1) >= len(all):
                result = content[start_span[0]:]
            else:
                end = all[num_sections-1]
                end_index = end.span()[0]
                result = content[start_span[0]:end_index + start_span[1]]

            # If there are any images, find them, copy them
            # result = copy_markdown_images(root, result)
            return result
        else:
            return "Heading reference beginning in # is required"
