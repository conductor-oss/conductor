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
/** True when the panel should offer the prompt tab. */
export declare function hasPromptMessages(input: unknown): boolean;
/**
 * Splits a message around an embedded JSON payload so tool results and recalled
 * context can be pretty-printed instead of shown as one unbroken line.
 *
 * Scans to the payload's matching close brace, not to the end of the message,
 * since prompts often state the instruction after the payload:
 * `[TOOL RESULTS]\n[{…}]\n[/TOOL RESULTS]\n\nProduce analyses for…`. Fenced
 * blocks are skipped; lifting one out would orphan its ``` delimiters.
 */
export declare function splitStructuredContent(text: string): StructuredContent | undefined;
/** Builds the preview entries for a prompt payload. */
export declare function buildPromptEntries(input: unknown): PromptEntry[];
/** One line: "Preview: agent instructions · 2 conversation messages." */
export declare function describePrompt(entries: PromptEntry[]): string;
