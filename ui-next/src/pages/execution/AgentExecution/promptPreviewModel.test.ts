import { describe, expect, it } from "vitest";

import {
  buildPromptEntries,
  describePrompt,
  hasPromptMessages,
  splitStructuredContent,
} from "./promptPreviewModel";

describe("hasPromptMessages", () => {
  it.each([
    [undefined, false],
    ["a string", false],
    [{ message: "only the last message" }, false],
    [{ messages: "not an array" }, false],
    [{ messages: [] }, false],
    [{ messages: [{ role: "user", message: "hi" }] }, true],
  ])("returns %j → %s", (input, expected) => {
    expect(hasPromptMessages(input)).toBe(expected);
  });
});

describe("buildPromptEntries", () => {
  it("keeps message order and reads text from `message` or `content`", () => {
    const entries = buildPromptEntries({
      messages: [
        { role: "system", message: "system context" },
        { role: "user", message: "the first question" },
        { role: "assistant", content: "an answer" },
      ],
    });

    expect(entries.map((entry) => [entry.role, entry.text])).toEqual([
      ["system", "system context"],
      ["user", "the first question"],
      ["assistant", "an answer"],
    ]);
  });

  it("classifies the leading system message as instructions when it matches", () => {
    const instructions = "You are a helpful agent.";
    const entries = buildPromptEntries({
      instructions,
      messages: [
        { role: "system", message: instructions },
        { role: "system", message: "injected recall context" },
        { role: "user", message: "hi" },
      ],
    });

    expect(entries.map((entry) => entry.kind)).toEqual([
      "instructions",
      "system",
      "conversation",
    ]);
  });

  it("never lifts a payload out of the instructions, which render as Markdown", () => {
    const instructions = 'Reply with:\n{"answer": "..."}';
    const entries = buildPromptEntries({
      instructions,
      messages: [
        { role: "system", message: instructions },
        { role: "user", message: 'Context:\n{"answer": "..."}' },
      ],
    });

    expect(entries[0].structured).toBeUndefined();
    expect(entries[0].text).toBe(instructions);
    expect(entries[1].structured?.payload).toEqual({ answer: "..." });
  });

  it("leaves a system message alone when it does not match the instructions", () => {
    const entries = buildPromptEntries({
      instructions: "You are a helpful agent.",
      messages: [{ role: "system", message: "something else" }],
    });

    expect(entries[0].kind).toBe("system");
  });

  it("unescapes line breaks for display", () => {
    const [entry] = buildPromptEntries({
      messages: [{ role: "user", message: "line one\\nline two" }],
    });

    expect(entry.text).toBe("line one\nline two");
  });

  it("leaves a literal \\n alone when the message already has real line breaks", () => {
    const [entry] = buildPromptEntries({
      messages: [
        {
          role: "user",
          message: "Join the items with \\n between them.\nThen stop.",
        },
      ],
    });

    expect(entry.text).toBe(
      "Join the items with \\n between them.\nThen stop.",
    );
  });

  it("reports the length of the message as persisted, payload included", () => {
    const message = 'before\n{"a": 1}\nafter';
    const [entry] = buildPromptEntries({
      messages: [{ role: "user", message }],
    });

    // The card splits this into prose and payload; the header reports the whole.
    expect(entry.structured).toBeDefined();
    expect(entry.length).toBe(message.length);
  });

  it("reports a nonzero length for a whole-JSON message with no prose", () => {
    const [entry] = buildPromptEntries({
      messages: [{ role: "user", content: { parts: ["a", "b"] } }],
    });

    expect(entry.structured?.leading).toBe("");
    expect(entry.structured?.trailing).toBe("");
    expect(entry.length).toBeGreaterThan(0);
  });

  it("renders a tool_call turn from its toolCalls, since it carries no text", () => {
    const toolCalls = [
      { name: "search_web", inputParameters: { query: "renewables" } },
    ];
    const [entry] = buildPromptEntries({
      messages: [{ role: "tool_call", toolCalls }],
    });

    expect(entry.length).toBeGreaterThan(0);
    expect(entry.structured?.payload).toEqual(toolCalls);
  });

  it("prefers a tool result's own text over its echoed toolCalls", () => {
    const [entry] = buildPromptEntries({
      messages: [
        {
          role: "tool",
          message: '{"results": []}',
          toolCalls: [{ name: "search_web", output: { results: [] } }],
        },
      ],
    });

    expect(entry.structured?.payload).toEqual({ results: [] });
  });

  it("skips non-object messages and falls back to an unknown role", () => {
    const entries = buildPromptEntries({
      messages: [null, "loose string", { message: "no role" }],
    });

    expect(entries).toEqual([
      { kind: "conversation", role: "unknown", text: "no role", length: 7 },
    ]);
  });

  it("serializes non-string content rather than dropping it", () => {
    const [entry] = buildPromptEntries({
      messages: [{ role: "user", content: { parts: ["a", "b"] } }],
    });

    expect(entry.structured?.payload).toEqual({ parts: ["a", "b"] });
  });
});

describe("splitStructuredContent", () => {
  it("splits prose from a trailing payload", () => {
    const result = splitStructuredContent(
      'Use the records below.\n{"records": [{"id": "a"}]}',
    );

    expect(result).toEqual({
      leading: "Use the records below.",
      payload: { records: [{ id: "a" }] },
      trailing: "",
    });
  });

  it("keeps the instruction that follows a delimiter-wrapped payload", () => {
    // Tool-result injection: a "[" tag line, the payload, a closing tag, the ask.
    const result = splitStructuredContent(
      [
        "[TOOL RESULTS]",
        JSON.stringify([{ output: { result: "## Executive Summary" } }]),
        "[/TOOL RESULTS]",
        "",
        "Produce comprehensive analyses for each domain.",
      ].join("\n"),
    );

    expect(result?.leading).toBe("[TOOL RESULTS]");
    expect(result?.trailing).toBe(
      "[/TOOL RESULTS]\n\nProduce comprehensive analyses for each domain.",
    );
  });

  it("finds the payload end across nesting and bracket-bearing strings", () => {
    const payload = {
      items: [
        { note: "closes } and ] inside a string", nested: { deep: [1, 2] } },
      ],
    };

    const result = splitStructuredContent(
      `head\n${JSON.stringify(payload)}\ntail-sentinel`,
    );

    expect(result?.payload).toEqual(payload);
    expect(result?.trailing).toBe("tail-sentinel");
  });

  it("keeps scanning past a bracketed line that is not JSON", () => {
    const result = splitStructuredContent('[NOT JSON]\n[{"ok": true}]');

    expect(result?.leading).toBe("[NOT JSON]");
    expect(result?.payload).toEqual([{ ok: true }]);
  });

  it("reports no prose for a whole-JSON message", () => {
    expect(splitStructuredContent('{"only": "json"}')).toEqual({
      leading: "",
      payload: { only: "json" },
      trailing: "",
    });
  });

  it.each([
    ["plain prose with no payload", "Nothing structured here."],
    [
      "a payload that never closes",
      'Context follows.\n[{"unterminated": true}',
    ],
    ["invalid JSON", 'Context follows.\n{"records": [broken'],
  ])("returns undefined for %s", (_case, text) => {
    expect(splitStructuredContent(text)).toBeUndefined();
  });

  it("leaves a bracketed prose list inline", () => {
    expect(
      splitStructuredContent("Rate 1-5.\n[1, 2, 3]\nthanks"),
    ).toBeUndefined();
  });

  it("splits a collection of records", () => {
    expect(
      splitStructuredContent('Results:\n[{"id": 1}, {"id": 2}]')?.payload,
    ).toEqual([{ id: 1 }, { id: 2 }]);
  });

  it("leaves a fenced payload alone rather than orphaning its delimiters", () => {
    const text = 'Here is the schema:\n```json\n{"a": 1}\n```\nDo X.';

    expect(splitStructuredContent(text)).toBeUndefined();
  });

  it("still splits a payload after a closed fence", () => {
    const text = 'See:\n```\nexample\n```\n{"a": 1}';

    expect(splitStructuredContent(text)?.payload).toEqual({ a: 1 });
  });
});

describe("describePrompt", () => {
  it("names each group present, pluralized", () => {
    const summary = describePrompt(
      buildPromptEntries({
        instructions: "do the thing",
        messages: [
          { role: "system", message: "do the thing" },
          { role: "system", message: "recall a" },
          { role: "system", message: "recall b" },
          { role: "user", message: "hi" },
        ],
      }),
    );

    expect(summary).toBe(
      "Preview: agent instructions · 2 system context messages · 1 conversation message.",
    );
  });

  it("omits groups with no messages", () => {
    const summary = describePrompt(
      buildPromptEntries({ messages: [{ role: "user", message: "hi" }] }),
    );

    expect(summary).toBe("Preview: 1 conversation message.");
  });

  it("is empty when there is nothing to describe", () => {
    expect(describePrompt([])).toBe("");
  });
});
