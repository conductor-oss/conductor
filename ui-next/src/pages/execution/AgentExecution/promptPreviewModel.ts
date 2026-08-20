/**
 * Turns a persisted LLM_CHAT_COMPLETE prompt payload into the entries
 * PromptPreview renders.
 */

/** A persisted chat message; field shapes vary by producer. */
export type PromptMessage = {
  role?: unknown;
  message?: unknown;
  content?: unknown;
  toolCalls?: unknown;
};

/**
 * - `instructions` — the agent's own instructions, echoed as the leading system message
 * - `system` — any other system message (injected recall, guardrails)
 * - `conversation` — user and assistant turns
 */
export type PromptEntryKind = "instructions" | "system" | "conversation";

/** Prose and payload of a message that embeds JSON. */
export type StructuredContent = {
  /** Prose before the payload; empty when the message opens with JSON. */
  leading: string;
  payload: unknown;
  /** Prose after the payload: closing delimiters, often the instruction. */
  trailing: string;
};

export type PromptEntry = {
  kind: PromptEntryKind;
  /** Role as persisted, used as the card label for non-instruction entries. */
  role: string;
  /** Message text, with "\\n" unescaped where that is safe. */
  text: string;
  /** Length as persisted, not `text.length`: a split entry never holds it whole. */
  length: number;
  /** Set when the message embeds a payload; the card renders this instead of `text`. */
  structured?: StructuredContent;
};

const UNKNOWN_ROLE = "unknown";

/** A fenced-code delimiter, indented up to three spaces; any info string is ignored. */
const FENCE_PATTERN = /^ {0,3}(?:`{3,}|~{3,})/;

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

/**
 * Messages persist their text under either `message` or `content`. A tool_call
 * turn has neither: ChatMessage carries the calls it is asking for in
 * `toolCalls` and leaves the text null, so fall back to those.
 */
function messageText(message: PromptMessage): string {
  const value = message.message || message.content || message.toolCalls;
  if (typeof value === "string") return value;
  return value == null ? "" : JSON.stringify(value, null, 2);
}

function messageRole(message: PromptMessage): string {
  return typeof message.role === "string" ? message.role : UNKNOWN_ROLE;
}

/** True when the panel should offer the prompt tab. */
export function hasPromptMessages(input: unknown): boolean {
  return readMessages(input).length > 0;
}

/**
 * Index just past the JSON value opening at `start`, or -1 if it never closes.
 * Only the outer bracket kind is counted; the other stays balanced in between.
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

/**
 * An object, or a collection of objects. Arrays of scalars are excluded so a
 * bracketed prose list ("rate it [1, 2, 3]") stays inline.
 */
function isPayload(value: unknown): boolean {
  if (value == null || typeof value !== "object") return false;
  if (!Array.isArray(value)) return true;
  return typeof value[0] === "object" && value[0] != null;
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
    return isPayload(payload) ? { payload, end } : undefined;
  } catch {
    return undefined;
  }
}

/**
 * Splits a message around an embedded JSON payload so tool results and recalled
 * context can be pretty-printed instead of shown as one unbroken line.
 *
 * Scans to the payload's matching close brace, not to the end of the message,
 * since prompts often state the instruction after the payload:
 * `[TOOL RESULTS]\n[{…}]\n[/TOOL RESULTS]\n\nProduce analyses for…`. Fenced
 * blocks are skipped; lifting one out would orphan its ``` delimiters.
 */
export function splitStructuredContent(
  text: string,
): StructuredContent | undefined {
  let offset = 0;
  let fenceChar: string | undefined;

  for (const line of text.split("\n")) {
    const lineStart = offset;
    offset += line.length + 1;

    const fence = FENCE_PATTERN.exec(line)?.[0].trimStart();
    if (fence) {
      // Only the same delimiter character closes a fence.
      if (fenceChar == null) fenceChar = fence[0];
      else if (fence[0] === fenceChar) fenceChar = undefined;
      continue;
    }
    if (fenceChar != null) continue;

    const opener = /^\s*[[{]/.exec(line);
    if (!opener) continue;
    // A line can open with a bracket and not be JSON (`[TOOL RESULTS]`).
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

/** Compiled agent tasks persist their instructions as the first system message. */
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

/** Builds the preview entries for a prompt payload. */
export function buildPromptEntries(input: unknown): PromptEntry[] {
  const messages = readMessages(input);
  const hasLeadingInstructions = startsWithInstructions(
    messages,
    readInstructions(input),
  );

  return messages.map((message, index) => {
    const raw = messageText(message);
    const kind = entryKind(message, hasLeadingInstructions && index === 0);
    return {
      kind,
      role: messageRole(message),
      // Unescape "\n" only when the text has no real line breaks: a
      // double-escaped payload has none, prose that merely mentions "\n" does.
      text: raw.includes("\n") ? raw : raw.replaceAll("\\n", "\n"),
      length: raw.length,
      // Instructions always render as Markdown, so nothing is lifted out of
      // them. Split the raw text: JSON.parse needs the original escaping.
      structured:
        kind === "instructions" ? undefined : splitStructuredContent(raw),
    };
  });
}

function pluralize(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? "" : "s"}`;
}

/** One line: "Preview: agent instructions · 2 conversation messages." */
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
