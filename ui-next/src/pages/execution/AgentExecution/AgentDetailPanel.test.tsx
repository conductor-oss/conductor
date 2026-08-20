import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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

describe("AgentDetailPanel prompt tab", () => {
  it("offers the tab only when the input carries messages", () => {
    const { unmount } = renderPanel(
      llmNode({ message: "just the last message" }),
    );
    expect(screen.queryByText("Prompt (Preview)")).toBeNull();
    unmount();

    renderPanel(llmNode({ messages: [{ role: "user", message: "hello" }] }));
    expect(screen.getByText("Prompt (Preview)")).toBeTruthy();
  });

  it("hides the tab for non-LLM nodes", () => {
    renderPanel({
      ...llmNode({ messages: [{ role: "user", message: "hello" }] }),
      kind: "tool",
    });

    expect(screen.queryByText("Prompt (Preview)")).toBeNull();
  });

  it("renders the preview when the tab is selected", () => {
    renderPanel(
      llmNode({ messages: [{ role: "user", message: "the question" }] }),
    );
    fireEvent.click(screen.getByText("Prompt (Preview)"));

    expect(screen.getByText("the question")).toBeTruthy();
  });

  it("keeps the Input tab an exact raw-payload view", () => {
    renderPanel(
      llmNode({
        instructions: "You are a helpful agent.",
        messages: [{ role: "user", message: "hi" }],
        message: "hi",
      }),
    );
    fireEvent.click(screen.getByText("Input"));

    // Objects go straight to the (mocked) JSON editor — no prompt formatting.
    expect(screen.getByText("Task input")).toBeTruthy();
    expect(screen.queryByText("Instructions")).toBeNull();
    expect(screen.queryByText("Structured data")).toBeNull();
  });

  it("falls back to Summary when the selected tab's data disappears (e.g. switching attempts)", async () => {
    const { rerender } = renderPanel(
      llmNode({ messages: [{ role: "user", message: "hello" }] }),
    );
    fireEvent.click(screen.getByText("Prompt (Preview)"));
    expect(screen.getByText("hello")).toBeTruthy();

    // Same node identity (label + kind), but the input backing the prompt tab is gone —
    // e.g. picking a past attempt whose inputData carries no messages.
    rerender(
      <AgentDetailPanel
        node={llmNode({ message: "just the last message" })}
        onClose={vi.fn()}
      />,
    );

    // waitFor, not a bare assertion: MUI's Tabs repositions its indicator
    // asynchronously when the tab set shrinks, and flushing that here keeps the
    // update inside act() instead of warning after the test body returns.
    await waitFor(() => {
      // Falls back to the Summary tab body rather than an empty prompt pane.
      expect(screen.getByText("Kind")).toBeTruthy();
    });
    expect(screen.queryByText("Prompt (Preview)")).toBeNull();
    expect(screen.queryByText("hello")).toBeNull();
  });
});
