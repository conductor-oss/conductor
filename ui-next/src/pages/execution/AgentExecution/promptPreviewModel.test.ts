import { describe, expect, it } from "vitest";

import {
  buildPromptEntries,
  describePrompt,
  hasPromptMessages,
  reflowInstructions,
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

  it("skips non-object messages and falls back to an unknown role", () => {
    const entries = buildPromptEntries({
      messages: [null, "loose string", { message: "no role" }],
    });

    expect(entries).toEqual([
      { kind: "conversation", role: "unknown", text: "no role" },
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
    // The shape emitted by tool-result injection: an opening tag whose own line
    // starts with "[", the payload, a closing tag, then the real instruction.
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
});

describe("reflowInstructions", () => {
  it("joins hard-wrapped lines and keeps list items on their own lines", () => {
    const reflowed = reflowInstructions(
      ["You coordinate", "the investigation.", "", "1. Ask.", "2. Cite."].join(
        "\n",
      ),
    );

    expect(reflowed).toBe(
      "You coordinate the investigation.\n\n1. Ask.\n2. Cite.",
    );
  });

  it("promotes a leading RULES label to a heading", () => {
    expect(reflowInstructions("RULES\n1. Ask before acting.")).toBe(
      "## Rules\n\n1. Ask before acting.",
    );
  });

  it("drops blank paragraphs and surrounding whitespace", () => {
    expect(reflowInstructions("\n\n  one  \n\n\n  two  \n\n")).toBe(
      "one\n\ntwo",
    );
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
