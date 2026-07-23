import html
import os
import re
import subprocess
import sys


VISUAL_JOURNEY_PAGES = {
    "quickstart/index.md",
    "devguide/ai/a2a-integration.md",
    "devguide/ai/agent-framework-recipes.md",
    "devguide/ai/conductor-agents.md",
    "devguide/ai/durable-agents.md",
    "devguide/ai/first-ai-agent.md",
    "devguide/ai/human-in-the-loop.md",
    "devguide/ai/mcp-guide.md",
}


def on_pre_page_macros(env):
    """Place each compact page description directly after its H1.

    Dedicated tutorial journeys already provide a richer visual introduction.
    For the remaining public pages, reuse front-matter descriptions and remove
    an identical first paragraph so the summary is additive only when needed.
    """
    page = env.page
    description = (page.meta or {}).get("description")
    if not description or page.file.src_path in VISUAL_JOURNEY_PAGES:
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

    summary = (
        '<aside class="page-summary" aria-label="Page summary">\n'
        '  <span class="page-summary__mark" aria-hidden="true">→</span>\n'
        "  <div>\n"
        '    <p class="page-summary__eyebrow">Conductor documentation</p>\n'
        f'    <p class="page-summary__text">{html.escape(summary_text)}</p>\n'
        "  </div>\n"
        "</aside>"
    )
    env.markdown = env.markdown[: heading.end()] + "\n" + summary + "\n\n" + remaining

def on_pre_build(env):
    """Fetch SDK README files before the build starts."""
    script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scripts", "fetch-sdk-docs.py")
    if os.path.exists(script):
        print("Fetching SDK documentation from GitHub...")
        subprocess.run([sys.executable, script], check=False)

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
