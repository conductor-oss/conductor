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

    // The card splits this into prose + a JSON viewer, but the header still
    // describes how big the message is, so neither piece alone is the length.
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

  it("keeps `-` bullet list items on their own lines", () => {
    const reflowed = reflowInstructions(
      ["Steps:", "- alpha", "- beta"].join("\n"),
    );

    expect(reflowed).toBe("Steps:\n- alpha\n- beta");
  });

  it("keeps `*` bullet list items on their own lines", () => {
    const reflowed = reflowInstructions(
      ["Steps:", "* alpha", "* beta"].join("\n"),
    );

    expect(reflowed).toBe("Steps:\n* alpha\n* beta");
  });

  it("joins wrapped prose but keeps a mixed prose+bullet paragraph's items separate", () => {
    const reflowed = reflowInstructions(
      [
        "You coordinate",
        "the investigation.",
        "- gather evidence",
        "- write the report",
      ].join("\n"),
    );

    expect(reflowed).toBe(
      "You coordinate the investigation.\n- gather evidence\n- write the report",
    );
  });

  it("leaves an all-caps list item as a list item, not a heading", () => {
    expect(reflowInstructions("1. STEP ONE\n2. STEP TWO")).toBe(
      "1. STEP ONE\n2. STEP TWO",
    );
    expect(reflowInstructions("- NEVER GUESS\n- ALWAYS CITE")).toBe(
      "- NEVER GUESS\n- ALWAYS CITE",
    );
  });

  it("leaves an all-caps table row as a table row, not a heading", () => {
    expect(reflowInstructions("| A | B |\n| 1 | 2 |")).toBe(
      "| A | B |\n| 1 | 2 |",
    );
  });

  it("promotes a leading RULES label to a heading", () => {
    expect(reflowInstructions("RULES\n1. Ask before acting.")).toBe(
      "## Rules\n\n1. Ask before acting.",
    );
  });

  it("promotes a multi-word caps label to a Title Case heading", () => {
    expect(
      reflowInstructions("OUTPUT FORMAT\nReturn a single JSON object."),
    ).toBe("## Output Format\n\nReturn a single JSON object.");
  });

  it("does not promote a long shouted sentence to a heading", () => {
    const reflowed = reflowInstructions(
      "DO NOT EVER CALL THIS TOOL WITHOUT CONFIRMING WITH THE USER FIRST.",
    );

    expect(reflowed).toBe(
      "DO NOT EVER CALL THIS TOOL WITHOUT CONFIRMING WITH THE USER FIRST.",
    );
    expect(reflowed.startsWith("##")).toBe(false);
  });

  it("does not promote a caps label inside a fenced block", () => {
    const reflowed = reflowInstructions(
      ["```", "RULES", "1. Ask before acting.", "```"].join("\n"),
    );

    expect(reflowed).toBe(
      ["```", "RULES", "1. Ask before acting.", "```"].join("\n"),
    );
  });

  it("does not promote a bracketed delimiter to a heading", () => {
    const reflowed = reflowInstructions(
      ["[TOOL RESULTS]", "Some trailing note."].join("\n"),
    );

    expect(reflowed).toBe("[TOOL RESULTS] Some trailing note.");
  });

  it("drops blank paragraphs and surrounding whitespace", () => {
    expect(reflowInstructions("\n\n  one  \n\n\n  two  \n\n")).toBe(
      "one\n\ntwo",
    );
  });

  it("keeps a fenced JSON block verbatim instead of reflowing it into one line", () => {
    const reflowed = reflowInstructions(
      [
        "Respond using this shape:",
        "",
        "```json",
        "{",
        '  "answer": "text"',
        "}",
        "```",
        "",
        "Do not add prose.",
      ].join("\n"),
    );

    expect(reflowed).toBe(
      [
        "Respond using this shape:",
        "",
        "```json",
        "{",
        '  "answer": "text"',
        "}",
        "```",
        "",
        "Do not add prose.",
      ].join("\n"),
    );
  });

  it("keeps a blank line inside a fenced block intact", () => {
    const reflowed = reflowInstructions(
      ["```", "first", "", "second", "```"].join("\n"),
    );

    expect(reflowed).toBe(["```", "first", "", "second", "```"].join("\n"));
  });

  it("treats an unclosed fence as running to the end of the text, verbatim", () => {
    const reflowed = reflowInstructions(
      [
        "Before.",
        "",
        "```json",
        "{",
        '  "key": "value"',
        "}",
        "",
        "More text that never gets closed.",
      ].join("\n"),
    );

    expect(reflowed).toBe(
      [
        "Before.",
        "",
        "```json",
        "{",
        '  "key": "value"',
        "}",
        "",
        "More text that never gets closed.",
      ].join("\n"),
    );
  });

  it("keeps Markdown table rows one per line instead of joining them", () => {
    const reflowed = reflowInstructions(["| a | b |", "| 1 | 2 |"].join("\n"));

    expect(reflowed).toBe("| a | b |\n| 1 | 2 |");
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
