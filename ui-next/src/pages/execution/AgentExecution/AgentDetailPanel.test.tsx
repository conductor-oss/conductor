import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { AgentDetailPanel, type DetailNodeData } from "./AgentDetailPanel";
import { AgentStatus, EventType } from "./types";

function llmNode(input: Record<string, unknown>): DetailNodeData {
  return {
    kind: "llm",
    label: "gpt-4o",
    status: AgentStatus.COMPLETED,
    event: {
      id: "llm-1",
      type: EventType.THINKING,
      timestamp: 0,
      toolName: "gpt-4o",
      summary: "gpt-4o",
      detail: { input, output: { result: "answer" } },
    } as any,
  };
}

function renderPanel(node: DetailNodeData) {
  return render(<AgentDetailPanel node={node} onClose={vi.fn()} />);
}

function openPromptTab() {
  fireEvent.click(screen.getByText("Prompt (Preview)"));
}

describe("AgentDetailPanel prompt preview", () => {
  it("offers the prompt tab only when messages are present", () => {
    const { unmount } = renderPanel(
      llmNode({ message: "just the last message" }),
    );
    expect(screen.queryByText("Prompt (Preview)")).toBeNull();
    unmount();

    renderPanel(llmNode({ messages: [{ role: "user", message: "hello" }] }));
    expect(screen.getByText("Prompt (Preview)")).toBeTruthy();
  });

  it("hides the prompt tab for non-LLM nodes", () => {
    renderPanel({
      ...llmNode({ messages: [{ role: "user", message: "hello" }] }),
      kind: "tool",
    });
    expect(screen.queryByText("Prompt (Preview)")).toBeNull();
  });

  it("renders every message in order with role and character count", () => {
    renderPanel(
      llmNode({
        messages: [
          { role: "system", message: "system context" },
          { role: "user", message: "the first question" },
          { role: "assistant", content: "an answer" },
        ],
      }),
    );
    openPromptTab();

    const roles = screen
      .getAllByText(/^(system|user|assistant)$/)
      .map((node) => node.textContent);
    expect(roles).toEqual(["system", "user", "assistant"]);
    expect(screen.getByText("system context")).toBeTruthy();
    expect(screen.getByText("the first question")).toBeTruthy();
    // `content` is supported alongside `message`.
    expect(screen.getByText("an answer")).toBeTruthy();
    expect(screen.getByText("14 characters")).toBeTruthy();
    expect(screen.getByText("18 characters")).toBeTruthy();
    expect(screen.getByText("9 characters")).toBeTruthy();
  });

  it("safely renders non-string content", () => {
    renderPanel(
      llmNode({
        messages: [{ role: "user", content: { parts: ["a", "b"] } }],
      }),
    );
    openPromptTab();

    // Serialized object content is detected as structured data, not crashed on.
    expect(screen.getByText("Structured data")).toBeTruthy();
  });

  it("shows escaped newlines as real newlines in the preview", () => {
    const { container } = renderPanel(
      llmNode({ messages: [{ role: "user", message: "line one\\nline two" }] }),
    );
    openPromptTab();

    // getByText normalizes whitespace, so assert on the rendered text directly.
    expect(container.textContent).toContain("line one\nline two");
    expect(container.textContent).not.toContain("line one\\nline two");
  });

  it("labels the leading system message as instructions when it matches inputData.instructions", () => {
    const instructions = "You are a helpful agent.";
    renderPanel(
      llmNode({
        instructions,
        messages: [
          { role: "system", message: instructions },
          { role: "user", message: "hi" },
        ],
      }),
    );
    openPromptTab();

    expect(screen.getByText("Instructions")).toBeTruthy();
    expect(screen.queryByText("system")).toBeNull();
  });

  it("reflows instructions and promotes a leading RULES label to a heading", () => {
    const instructions = [
      "You coordinate",
      "the investigation.",
      "",
      "RULES",
      "1. Ask before acting.",
      "2. Cite evidence.",
    ].join("\n");
    renderPanel(
      llmNode({
        instructions,
        messages: [{ role: "system", message: instructions }],
      }),
    );
    openPromptTab();

    // Wrapped paragraph lines are joined; list items keep their own lines.
    expect(screen.getByText("You coordinate the investigation.")).toBeTruthy();
    const heading = screen.getByText("Rules");
    expect(heading.tagName).toBe("H2");
    const rules = screen.getAllByRole("listitem").map((li) => li.textContent);
    expect(rules).toEqual(["Ask before acting.", "Cite evidence."]);
  });

  it.each([["system"], ["user"], ["assistant"]])(
    "collapses a long %s message and toggles it",
    (role) => {
      const long = `${"x".repeat(1300)} tail-marker`;
      renderPanel(llmNode({ messages: [{ role, message: long }] }));
      openPromptTab();

      expect(screen.queryByText(/tail-marker/)).toBeNull();
      fireEvent.click(
        screen.getByRole("button", { name: "Show full message" }),
      );
      expect(screen.getByText(/tail-marker/)).toBeTruthy();

      fireEvent.click(screen.getByRole("button", { name: "Show less" }));
      expect(screen.queryByText(/tail-marker/)).toBeNull();
    },
  );

  it("calls the toggle an instruction on the instructions card", () => {
    const instructions = `${"x".repeat(1300)} tail-marker`;
    renderPanel(
      llmNode({
        instructions,
        messages: [{ role: "system", message: instructions }],
      }),
    );
    openPromptTab();

    fireEvent.click(
      screen.getByRole("button", { name: "Show full instruction" }),
    );
    expect(screen.getByText(/tail-marker/)).toBeTruthy();
  });

  it("collapses each long message independently", () => {
    renderPanel(
      llmNode({
        messages: [
          { role: "user", message: `${"a".repeat(1300)} first-tail` },
          { role: "user", message: `${"b".repeat(1300)} second-tail` },
        ],
      }),
    );
    openPromptTab();

    const toggles = screen.getAllByRole("button", {
      name: "Show full message",
    });
    expect(toggles).toHaveLength(2);
    fireEvent.click(toggles[1]);
    expect(screen.getByText(/second-tail/)).toBeTruthy();
    expect(screen.queryByText(/first-tail/)).toBeNull();
  });

  it.each([["system"], ["user"]])(
    "does not collapse a short %s message",
    (role) => {
      renderPanel(llmNode({ messages: [{ role, message: "short" }] }));
      openPromptTab();

      expect(screen.queryByRole("button", { name: /^Show full/ })).toBeNull();
    },
  );

  it("does not collapse a long structured message", () => {
    const payload = JSON.stringify({
      items: Array.from({ length: 200 }, (_, i) => ({ id: i })),
    });
    expect(payload.length).toBeGreaterThan(1200);
    renderPanel(
      llmNode({
        messages: [{ role: "user", message: `Context:\n${payload}` }],
      }),
    );
    openPromptTab();

    expect(screen.getByText("Structured data")).toBeTruthy();
    expect(screen.queryByRole("button", { name: /^Show full/ })).toBeNull();
  });

  it("renders leading text normally and trailing JSON as structured data", () => {
    const message = [
      "# Relevant prior context",
      "Use the records below.",
      '{"records": [{"id": "a"}]}',
    ].join("\n");
    renderPanel(llmNode({ messages: [{ role: "system", message }] }));
    openPromptTab();

    expect(screen.getByText("Use the records below.")).toBeTruthy();
    expect(screen.getByText("Structured data")).toBeTruthy();
  });

  it("renders a delimiter-wrapped payload with the instruction that follows it", () => {
    // The shape emitted by tool-result injection: an opening tag whose own line
    // starts with "[", the payload, a closing tag, then the real instruction.
    const message = [
      "[TOOL RESULTS]",
      JSON.stringify([{ output: { result: "## Executive Summary" } }]),
      "[/TOOL RESULTS]",
      "",
      "Produce comprehensive analyses for each domain.",
    ].join("\n");
    renderPanel(llmNode({ messages: [{ role: "user", message }] }));
    openPromptTab();

    expect(screen.getByText("[TOOL RESULTS]")).toBeTruthy();
    expect(screen.getByText("Structured data")).toBeTruthy();
    // The trailing instruction is the point of the prompt — it must survive.
    expect(
      screen.getByText(/Produce comprehensive analyses for each domain\./),
    ).toBeTruthy();
  });

  it("keeps text on both sides of a payload that is not at the end", () => {
    const message = `before\n{"a": 1}\nafter`;
    const { container } = renderPanel(
      llmNode({ messages: [{ role: "user", message }] }),
    );
    openPromptTab();

    expect(screen.getByText("before")).toBeTruthy();
    expect(screen.getByText("after")).toBeTruthy();
    expect(container.textContent).toContain("Structured data");
  });

  it("finds the payload end across nested containers and bracket-bearing strings", () => {
    const payload = {
      items: [
        { note: "closes } and ] inside a string", nested: { deep: [1, 2] } },
      ],
    };
    const message = `head\n${JSON.stringify(payload)}\ntail-sentinel`;
    renderPanel(llmNode({ messages: [{ role: "user", message }] }));
    openPromptTab();

    expect(screen.getByText("Structured data")).toBeTruthy();
    // A miscounted end brace would swallow or truncate the trailing prose.
    expect(screen.getByText("tail-sentinel")).toBeTruthy();
  });

  it("skips a bracketed line that is not JSON and keeps scanning", () => {
    const message = '[NOT JSON]\n[{"ok": true}]';
    renderPanel(llmNode({ messages: [{ role: "user", message }] }));
    openPromptTab();

    expect(screen.getByText("[NOT JSON]")).toBeTruthy();
    expect(screen.getByText("Structured data")).toBeTruthy();
  });

  it("treats a whole-JSON message as structured with no surrounding prose", () => {
    renderPanel(
      llmNode({ messages: [{ role: "user", message: '{"only": "json"}' }] }),
    );
    openPromptTab();

    expect(screen.getByText("Structured data")).toBeTruthy();
  });

  it("falls back to plain text when a payload never closes", () => {
    const message = 'Context follows.\n[{"unterminated": true}';
    renderPanel(llmNode({ messages: [{ role: "user", message }] }));
    openPromptTab();

    expect(screen.queryByText("Structured data")).toBeNull();
    expect(screen.getByText(/Context follows\./)).toBeTruthy();
  });

  it("falls back to plain text when the payload is not valid JSON", () => {
    const message = 'Context follows.\n{"records": [broken';
    renderPanel(llmNode({ messages: [{ role: "user", message }] }));
    openPromptTab();

    expect(screen.queryByText("Structured data")).toBeNull();
    expect(screen.getByText(/Context follows\./)).toBeTruthy();
  });

  it("keeps the Input tab an exact raw-payload view", () => {
    const input = {
      instructions: "You are a helpful agent.",
      messages: [{ role: "user", message: "hi" }],
      message: "hi",
    };
    renderPanel(llmNode(input));
    fireEvent.click(screen.getByText("Input"));

    // Objects go straight to the (mocked) JSON editor — no prompt formatting.
    expect(screen.getByText("Task input")).toBeTruthy();
    expect(screen.queryByText("Instructions")).toBeNull();
    expect(screen.queryByText("Structured data")).toBeNull();
  });
});
