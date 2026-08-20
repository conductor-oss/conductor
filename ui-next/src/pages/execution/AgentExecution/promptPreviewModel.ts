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
  /** Message text, readable: escaped line breaks in payloads become newlines. */
  text: string;
  /** Set when the message embeds a payload worth its own JSON viewer. */
  structured?: StructuredContent;
};

const UNKNOWN_ROLE = "unknown";
/** A paragraph whose first line is this label becomes a Markdown heading. */
const RULES_LABEL = "RULES";

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
    const isListItem = /^(?:[-*]|\d+[a-z]?)\.\s+/.test(line);
    return isListItem ? `${text}\n${line}` : `${text} ${line}`;
  }, "");
}

/**
 * Rejoins hard-wrapped prompt text into Markdown paragraphs so compiler output
 * reads as prose. Only affects the preview — the Input tab keeps the raw text.
 */
export function reflowInstructions(text: string): string {
  return text
    .trim()
    .split(/\n\s*\n+/)
    .map((paragraph) => {
      const lines = paragraph
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean);
      if (lines[0]?.toUpperCase() === RULES_LABEL) {
        return `## Rules\n\n${reflowParagraph(lines.slice(1))}`;
      }
      return reflowParagraph(lines);
    })
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
      // Escaped breaks are unescaped for display only; the payload split runs
      // on the raw text because JSON.parse needs the original escaping.
      text: raw.replaceAll("\\n", "\n"),
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
