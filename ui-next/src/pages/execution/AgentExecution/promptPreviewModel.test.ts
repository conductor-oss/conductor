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

/**
 * A persisted LLM_CHAT_COMPLETE payload, copied verbatim from a local
 * research_writer_48 run (task ac496bc7-6963-4f40-8d3b-94895e996b42). It
 * carries the shapes the preview has to survive together: the system prompt, a
 * delimiter-wrapped payload with the real ask after it, a tool_call turn with
 * no text at all, and the tool results that answered it.
 */
const REAL_MESSAGES = [
  {
    role: "system",
    message:
      "You are a research writer. Research topics thoroughly and write structured reports with multiple sections.\n\nBefore executing, create a step-by-step plan. Think through each step carefully, then execute the plan systematically using your available tools. After each step, verify progress before moving to the next.",
    media: [],
  },
  {
    role: "user",
    message:
      '[TOOL RESULTS]\n[{"output":{"query":"types of renewable energy and their benefits","results":["Renewables = 30% of global electricity (2023)","Solar capacity grew 50% year-over-year"]},"name":"search_web"},{"output":{"query":"current climate change challenges","results":["Solar energy costs dropped 89% since 2010","Wind power is cheapest in many regions"]},"name":"search_web"},{"output":{"query":"renewable energy solutions for climate change","results":["Solar energy costs dropped 89% since 2010","Wind power is cheapest in many regions"]},"name":"search_web"}]\n[/TOOL RESULTS]\n\nWrite a brief report on renewable energy and climate change solutions.',
    media: [],
  },
  {
    role: "tool_call",
    media: [],
    toolCalls: [
      {
        taskReferenceName: "call_PvGu3tY9KFAuXIniDnp5Phoj_0",
        name: "search_web",
        type: "SIMPLE",
        inputParameters: {
          method: "search_web",
          query: "types of renewable energy and their benefits",
        },
      },
      {
        taskReferenceName: "call_9zXNq9RQteUZQxXv6V2MJ8Ss_1",
        name: "search_web",
        type: "SIMPLE",
        inputParameters: {
          method: "search_web",
          query: "current climate change challenges",
        },
      },
      {
        taskReferenceName: "call_PHBy7bIe3s0hYKDRJZjbW4RR_2",
        name: "search_web",
        type: "SIMPLE",
        inputParameters: {
          method: "search_web",
          query: "renewable energy solutions for climate change",
        },
      },
    ],
  },
  {
    role: "tool",
    message:
      '{"query":"types of renewable energy and their benefits","results":["Renewables = 30% of global electricity (2023)","Solar capacity grew 50% year-over-year"]}',
    media: [],
    toolCalls: [
      {
        taskReferenceName: "call_PvGu3tY9KFAuXIniDnp5Phoj_0",
        name: "search_web",
        type: "search_web",
        inputParameters: {
          _agent_tool_name: "search_web",
          query: "types of renewable energy and their benefits",
        },
        output: {
          query: "types of renewable energy and their benefits",
          results: [
            "Renewables = 30% of global electricity (2023)",
            "Solar capacity grew 50% year-over-year",
          ],
        },
      },
    ],
  },
  {
    role: "tool",
    message:
      '{"query":"current climate change challenges","results":["Solar energy costs dropped 89% since 2010","Wind power is cheapest in many regions"]}',
    media: [],
    toolCalls: [
      {
        taskReferenceName: "call_9zXNq9RQteUZQxXv6V2MJ8Ss_1",
        name: "search_web",
        type: "search_web",
        inputParameters: {
          _agent_tool_name: "search_web",
          query: "current climate change challenges",
        },
        output: {
          query: "current climate change challenges",
          results: [
            "Solar energy costs dropped 89% since 2010",
            "Wind power is cheapest in many regions",
          ],
        },
      },
    ],
  },
  {
    role: "tool",
    message:
      '{"query":"renewable energy solutions for climate change","results":["Solar energy costs dropped 89% since 2010","Wind power is cheapest in many regions"]}',
    media: [],
    toolCalls: [
      {
        taskReferenceName: "call_PHBy7bIe3s0hYKDRJZjbW4RR_2",
        name: "search_web",
        type: "search_web",
        inputParameters: {
          _agent_tool_name: "search_web",
          query: "renewable energy solutions for climate change",
        },
        output: {
          query: "renewable energy solutions for climate change",
          results: [
            "Solar energy costs dropped 89% since 2010",
            "Wind power is cheapest in many regions",
          ],
        },
      },
    ],
  },
] as const;

/** What the transform hands the panel: the messages plus the resolved instructions. */
const REAL_PROMPT = {
  instructions: REAL_MESSAGES[0].message,
  messages: REAL_MESSAGES,
};

describe("a real persisted prompt", () => {
  const entries = buildPromptEntries(REAL_PROMPT);

  it("labels every turn and leaves none empty", () => {
    expect(
      entries.map((entry) => [entry.kind, entry.role, entry.length]),
    ).toEqual([
      ["instructions", "system", 313],
      ["conversation", "user", 653],
      ["conversation", "tool_call", 712],
      ["conversation", "tool", 157],
      ["conversation", "tool", 142],
      ["conversation", "tool", 154],
    ]);
  });

  it("keeps the ask that follows the user turn's payload", () => {
    expect(entries[1].structured?.leading).toBe("[TOOL RESULTS]");
    expect(entries[1].structured?.trailing).toBe(
      "[/TOOL RESULTS]\n\nWrite a brief report on renewable energy and climate change solutions.",
    );
  });

  it("renders the text-less tool_call turn from its three calls", () => {
    const payload = entries[2].structured?.payload as Array<{
      name: string;
      inputParameters: { query: string };
    }>;

    expect(payload.map((call) => call.name)).toEqual([
      "search_web",
      "search_web",
      "search_web",
    ]);
    expect(payload.map((call) => call.inputParameters.query)).toEqual([
      "types of renewable energy and their benefits",
      "current climate change challenges",
      "renewable energy solutions for climate change",
    ]);
  });

  it("summarizes the whole exchange", () => {
    expect(describePrompt(entries)).toBe(
      "Preview: agent instructions · 5 conversation messages.",
    );
  });
});
