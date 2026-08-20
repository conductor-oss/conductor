/**
 * Pure model behind the LLM "Prompt (Preview)" tab: turns a persisted
 * `LLM_CHAT_COMPLETE` input payload into the entries the preview renders.
 *
 * Deliberately React-free — the interesting rules (which system message is
 * really the agent's instructions, where an embedded JSON payload ends) are
 * parsing decisions, and they are tested as such.
 */

/** A persisted chat message. Producers differ, so no field shape is assumed. */
export type PromptMessage = {
  role?: unknown;
  message?: unknown;
  content?: unknown;
};

/**
 * How a message is presented:
 * - `instructions` — the agent's configured instructions, echoed as the leading
 *   system message; shown as reflowed Markdown under an "Instructions" label.
 * - `system` — any other system message (injected recall, guardrails, …).
 * - `conversation` — user and assistant turns.
 */
export type PromptEntryKind = "instructions" | "system" | "conversation";

/** Prose and payload of a message that embeds a JSON object or array. */
export type StructuredContent = {
  /** Prose before the payload; empty when the message opens with JSON. */
  leading: string;
  payload: unknown;
  /** Prose after the payload — closing delimiters, and often the instruction. */
  trailing: string;
};

export type PromptEntry = {
  kind: PromptEntryKind;
  /** Role as persisted, used as the card label for non-instruction entries. */
  role: string;
  /**
   * Message text, readable: escaped line breaks become newlines where that is
   * safe to do (see `buildPromptEntries`). Unused when `structured` is set —
   * that branch renders from `structured` instead.
   */
  text: string;
  /**
   * Length of the message exactly as persisted, which is what the card header
   * reports. Deliberately not `text.length`: for a structured entry the card
   * renders the prose and the payload separately, so neither piece alone
   * describes how big the message is, and the raw length does.
   */
  length: number;
  /** Set when the message embeds a payload worth its own JSON viewer. */
  structured?: StructuredContent;
};

const UNKNOWN_ROLE = "unknown";
/**
 * A prose paragraph whose first line is a short, standalone ALL-CAPS label
 * (`RULES`, `OUTPUT FORMAT`, `CONSTRAINTS`, …) is promoted to a Markdown `##`
 * heading, with the rest of the paragraph reflowing underneath as usual.
 *
 * The bounds are deliberately tight. Agent instructions also contain
 * genuinely shouted *sentences* for emphasis — e.g.
 * "DO NOT EVER CALL THIS TOOL WITHOUT CONFIRMING" — and those must stay
 * inline prose, not become a heading. Real section labels observed in
 * practice (RULES, CONSTRAINTS, OUTPUT FORMAT, TOOLS, EXAMPLES) are one or
 * two words and well inside the caps below, while a shouted imperative
 * sentence is a full clause, typically 5+ words. Word count is therefore
 * the primary signal; the character cap is just a backstop against a
 * couple of long compound words.
 */
const HEADING_LABEL_MAX_WORDS = 4;
const HEADING_LABEL_MAX_LENGTH = 30;
/**
 * Matches a candidate heading label: at least one uppercase letter, no
 * lowercase letters anywhere. This alone would still admit a bare `---`
 * divider or a `1.` list marker (neither contains a lowercase letter, but
 * neither contains a letter at all either), so the "has an uppercase
 * letter" half of the check is what rules those out.
 */
const ALL_CAPS_WITH_LETTER_PATTERN = /^[^a-z]*[A-Z][^a-z]*$/;

/**
 * Matches a Markdown list-item marker at the start of a line: `-`, `*`, `+`
 * bullets, or ordered markers like `1.`, `2)`, `1a.`, each followed by
 * whitespace. The two alternatives are bracketed separately so the trailing
 * `.`/`)` only binds to the ordered form, not the bullet form.
 */
const LIST_ITEM_PATTERN = /^(?:[-*+]|\d+[a-z]?[.)])\s+/;
/**
 * Matches a Markdown table row: a line starting with `|`. Reflow must not
 * join table rows together — `| a | b |\n| 1 | 2 |` collapsing into
 * `| a | b | | 1 | 2 |` no longer parses as a table.
 */
const TABLE_ROW_PATTERN = /^\|/;

/**
 * True when `line` reads as a short section label rather than prose.
 *
 * Bracketed delimiters like `[TOOL RESULTS]` are deliberately excluded even
 * though they are all-caps and short: that bracket convention marks a
 * payload boundary (see `splitStructuredContent`), not a section title, and
 * rendering it as a heading would claim a role it doesn't have.
 */
function isHeadingLabel(line: string): boolean {
  if (line.length === 0 || line.length > HEADING_LABEL_MAX_LENGTH) {
    return false;
  }
  if (line.startsWith("[") || line.startsWith("(")) return false;
  // An all-caps list item or table row is still list/table markup, and
  // reflowParagraph is what should be handling it — not a section title.
  if (LIST_ITEM_PATTERN.test(line) || TABLE_ROW_PATTERN.test(line)) {
    return false;
  }
  const words = line.split(/\s+/).filter(Boolean);
  if (words.length === 0 || words.length > HEADING_LABEL_MAX_WORDS) {
    return false;
  }
  return ALL_CAPS_WITH_LETTER_PATTERN.test(line);
}

/** Renders a heading label in Title Case, e.g. `OUTPUT FORMAT` → `Output Format`. */
function toTitleCase(label: string): string {
  return label.toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function readMessages(input: unknown): PromptMessage[] {
  if (input == null || typeof input !== "object") return [];
  const messages = (input as { messages?: unknown }).messages;
  if (!Array.isArray(messages)) return [];
  return messages.filter(
    (message): message is PromptMessage =>
      message != null && typeof message === "object",
  );
}

function readInstructions(input: unknown): string | undefined {
  if (input == null || typeof input !== "object") return undefined;
  const instructions = (input as { instructions?: unknown }).instructions;
  return typeof instructions === "string" ? instructions : undefined;
}

/** Messages persist their text under either `message` or `content`. */
function messageText(message: PromptMessage): string {
  const value = message.message ?? message.content;
  if (typeof value === "string") return value;
  return value == null ? "" : JSON.stringify(value, null, 2);
}

function messageRole(message: PromptMessage): string {
  return typeof message.role === "string" ? message.role : UNKNOWN_ROLE;
}

/** True when the panel should offer the prompt tab for this input payload. */
export function hasPromptMessages(input: unknown): boolean {
  return readMessages(input).length > 0;
}

/**
 * Index just past the JSON value opening at `start`, or -1 if it never closes.
 *
 * Only the outer container's own brackets are counted: JSON nests properly, so
 * the other kind stays balanced in between and cannot shift the depth.
 */
function jsonValueEnd(text: string, start: number): number {
  const open = text[start];
  if (open !== "{" && open !== "[") return -1;
  const close = open === "{" ? "}" : "]";
  let depth = 0;
  let inString = false;
  let escaped = false;

  for (let i = start; i < text.length; i++) {
    const char = text[i];
    if (inString) {
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === '"') inString = false;
      continue;
    }
    if (char === '"') inString = true;
    else if (char === open) depth++;
    else if (char === close && --depth === 0) return i + 1;
  }
  return -1;
}

/** Parses the JSON object/array opening at `start`, reporting where it ends. */
function parseJsonAt(
  text: string,
  start: number,
): { payload: unknown; end: number } | undefined {
  const end = jsonValueEnd(text, start);
  if (end < 0) return undefined;
  try {
    const payload = JSON.parse(text.slice(start, end));
    if (payload == null || typeof payload !== "object") return undefined;
    return { payload, end };
  } catch {
    return undefined;
  }
}

/**
 * Splits a message around an embedded JSON object/array so generated payloads
 * (tool results, recalled context, evidence blobs) can render in a JSON viewer
 * instead of as a wall of text.
 *
 * The payload is located by scanning to its matching close brace rather than
 * assuming it runs to the end of the message: agent prompts routinely wrap a
 * payload in delimiters and then state the actual instruction after it, e.g.
 * `[TOOL RESULTS]\n[{…}]\n[/TOOL RESULTS]\n\nProduce analyses for…`. Returns
 * undefined — and the caller falls back to plain text — when nothing parses.
 */
export function splitStructuredContent(
  text: string,
): StructuredContent | undefined {
  let offset = 0;
  for (const line of text.split("\n")) {
    const lineStart = offset;
    offset += line.length + 1;
    const opener = /^\s*[[{]/.exec(line);
    if (!opener) continue;
    // A line may legitimately open with a bracket without being JSON at all
    // (`[TOOL RESULTS]`); parse failures just move on to the next candidate.
    const start = lineStart + opener[0].length - 1;
    const found = parseJsonAt(text, start);
    if (found) {
      return {
        leading: text.slice(0, start).trim(),
        payload: found.payload,
        trailing: text.slice(found.end).trim(),
      };
    }
  }
  return undefined;
}

function reflowParagraph(lines: string[]): string {
  return lines.reduce((text, line) => {
    if (!text) return line;
    const staysOnOwnLine =
      LIST_ITEM_PATTERN.test(line) || TABLE_ROW_PATTERN.test(line);
    return staysOnOwnLine ? `${text}\n${line}` : `${text} ${line}`;
  }, "");
}

/**
 * One chunk of `reflowInstructions`' input, in order: either a fenced code
 * block kept byte-for-byte, or ordinary prose to be paragraph-reflowed.
 */
type ReflowSegment =
  | { kind: "fence"; lines: string[] }
  | { kind: "prose"; lines: string[] };

/**
 * Matches a fence delimiter line: \`\`\` or ~~~, three or more characters,
 * up to three leading spaces (Markdown allows a fenced block to be indented
 * that far and still count as unindented), and an optional trailing info
 * string (\`\`\`json). The run of fence characters is captured so the segmenter
 * can require a close line to repeat the *same* character, per CommonMark —
 * a stray \`\`\` must not close a ~~~ block or vice versa.
 */
const FENCE_OPEN_PATTERN = /^ {0,3}(`{3,}|~{3,})/;

/**
 * Splits text into fenced and unfenced regions so `reflowInstructions` can
 * leave fenced content untouched while still reflowing everything else.
 *
 * This has to be a line-oriented pass over the *whole* input, tracking fence
 * state as it goes, rather than something layered on top of the existing
 * blank-line paragraph split: a fenced block can legitimately contain a blank
 * line (pretty-printed JSON is full of them), and splitting on blank lines
 * first would sever a fence from its close and reflow both halves as prose.
 *
 * An opening fence that never closes swallows the remainder of the text
 * verbatim as part of that fence, rather than guessing where it should have
 * ended. Falling back to prose for the tail risks reflowing what is probably
 * still code, and there is no line at which resuming "unfenced" would be
 * clearly correct — so the fence just wins for the rest of the input.
 */
function segmentFences(text: string): ReflowSegment[] {
  const segments: ReflowSegment[] = [];
  let prose: string[] = [];
  const lines = text.split("\n");

  for (let i = 0; i < lines.length; i++) {
    const open = FENCE_OPEN_PATTERN.exec(lines[i]);
    if (!open) {
      prose.push(lines[i]);
      continue;
    }

    if (prose.length > 0) {
      segments.push({ kind: "prose", lines: prose });
      prose = [];
    }

    const marker = open[1];
    const closePattern = new RegExp(
      `^ {0,3}${marker[0]}{${marker.length},}\\s*$`,
    );
    const fence = [lines[i]];
    i++;
    while (i < lines.length) {
      fence.push(lines[i]);
      if (closePattern.test(lines[i])) break;
      i++;
    }
    segments.push({ kind: "fence", lines: fence });
  }

  if (prose.length > 0) segments.push({ kind: "prose", lines: prose });
  return segments;
}

/**
 * Paragraph-reflows one unfenced region: this is `reflowInstructions`' whole
 * behavior from before it became fence-aware, scoped to just a prose segment.
 */
function reflowProse(lines: string[]): string {
  return lines
    .join("\n")
    .trim()
    .split(/\n\s*\n+/)
    .map((paragraph) => {
      const paragraphLines = paragraph
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean);
      if (paragraphLines[0] && isHeadingLabel(paragraphLines[0])) {
        const heading = `## ${toTitleCase(paragraphLines[0])}`;
        const rest = reflowParagraph(paragraphLines.slice(1));
        return rest ? `${heading}\n\n${rest}` : heading;
      }
      return reflowParagraph(paragraphLines);
    })
    .filter(Boolean)
    .join("\n\n");
}

/**
 * Rejoins hard-wrapped prompt text into Markdown paragraphs so compiler output
 * reads as prose. Only affects the preview — the Input tab keeps the raw text.
 *
 * Fenced code blocks pass through verbatim, line breaks and all: reflowing a
 * JSON schema or example the same way as prose turns it into an unreadable
 * inline blob instead of a renderable code block (see `segmentFences`).
 */
export function reflowInstructions(text: string): string {
  return segmentFences(text.trim())
    .map((segment) =>
      segment.kind === "fence"
        ? segment.lines.join("\n")
        : reflowProse(segment.lines),
    )
    .filter(Boolean)
    .join("\n\n");
}

/**
 * Compiled agent tasks persist their configured instructions as the first
 * system message. Recognising that known duplicate lets the preview label it;
 * the raw Input tab still shows the payload exactly as persisted.
 */
function startsWithInstructions(
  messages: PromptMessage[],
  instructions: string | undefined,
): boolean {
  const first = messages[0];
  return (
    first != null &&
    messageRole(first) === "system" &&
    instructions != null &&
    messageText(first) === instructions
  );
}

function entryKind(
  message: PromptMessage,
  isLeadingInstructions: boolean,
): PromptEntryKind {
  if (isLeadingInstructions) return "instructions";
  return messageRole(message) === "system" ? "system" : "conversation";
}

/** Builds the preview entries for a persisted LLM task input payload. */
export function buildPromptEntries(input: unknown): PromptEntry[] {
  const messages = readMessages(input);
  const hasLeadingInstructions = startsWithInstructions(
    messages,
    readInstructions(input),
  );

  return messages.map((message, index) => {
    const raw = messageText(message);
    return {
      kind: entryKind(message, hasLeadingInstructions && index === 0),
      role: messageRole(message),
      // Unescape "\n" into a real line break only when the text has no real
      // line breaks already. A genuinely double-escaped payload (round-tripped
      // through an extra layer of JSON encoding upstream) never contains a
      // literal newline, so unescaping it is safe; ordinary prose that merely
      // mentions "\n" — as a delimiter, in a regex, while describing escaping —
      // is almost always interspersed with real newlines elsewhere in the same
      // message, so the gate leaves it untouched rather than splicing a line
      // break into the middle of a sentence.
      text: raw.includes("\n") ? raw : raw.replaceAll("\\n", "\n"),
      length: raw.length,
      // Splits on the raw text, not the unescaped text: JSON.parse needs the
      // original escaping to reproduce the payload faithfully.
      structured: splitStructuredContent(raw),
    };
  });
}

function pluralize(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? "" : "s"}`;
}

/** One-line summary of what the preview is showing, e.g. "Preview: agent instructions · 2 conversation messages." */
export function describePrompt(entries: PromptEntry[]): string {
  const countOf = (kind: PromptEntryKind) =>
    entries.filter((entry) => entry.kind === kind).length;
  const systemCount = countOf("system");
  const conversationCount = countOf("conversation");

  const parts = [
    countOf("instructions") > 0 ? "agent instructions" : undefined,
    systemCount > 0
      ? pluralize(systemCount, "system context message")
      : undefined,
    conversationCount > 0
      ? pluralize(conversationCount, "conversation message")
      : undefined,
  ].filter(Boolean);

  return parts.length > 0 ? `Preview: ${parts.join(" · ")}.` : "";
}
