import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { AgentDetailPanel, type DetailNodeData } from "./AgentDetailPanel";
import { AgentStatus, EventType } from "./types";

function llmNode(detail: {
  input?: unknown;
  prompt?: unknown;
}): DetailNodeData {
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
      detail: { output: { result: "answer" }, ...detail },
    } as any,
  };
}

function renderPanel(node: DetailNodeData) {
  return render(<AgentDetailPanel node={node} onClose={vi.fn()} />);
}

describe("AgentDetailPanel prompt tab", () => {
  it("offers the tab only when the event carries prompt messages", () => {
    const { unmount } = renderPanel(
      llmNode({ input: { message: "just the last message" } }),
    );
    expect(screen.queryByText("Prompt")).toBeNull();
    unmount();

    renderPanel(
      llmNode({ prompt: { messages: [{ role: "user", message: "hello" }] } }),
    );
    expect(screen.getByText("Prompt")).toBeTruthy();
  });

  it("hides the tab for non-LLM nodes", () => {
    renderPanel({
      ...llmNode({
        prompt: { messages: [{ role: "user", message: "hello" }] },
      }),
      kind: "tool",
    });

    expect(screen.queryByText("Prompt")).toBeNull();
  });

  it("renders the preview when the tab is selected", () => {
    renderPanel(
      llmNode({
        prompt: { messages: [{ role: "user", message: "the question" }] },
      }),
    );
    fireEvent.click(screen.getByText("Prompt"));

    expect(screen.getByText("the question")).toBeTruthy();
  });

  it("keeps the Input tab an exact raw-payload view", () => {
    renderPanel(
      llmNode({
        input: { instructions: "You are a helpful agent.", message: "hi" },
        prompt: { messages: [{ role: "user", message: "hi" }] },
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
      llmNode({ prompt: { messages: [{ role: "user", message: "hello" }] } }),
    );
    fireEvent.click(screen.getByText("Prompt"));
    expect(screen.getByText("hello")).toBeTruthy();

    // Same node identity, but the messages behind the prompt tab are gone.
    rerender(
      <AgentDetailPanel
        node={llmNode({ input: { message: "just the last message" } })}
        onClose={vi.fn()}
      />,
    );

    // waitFor, not a bare assertion: MUI's Tabs repositions its indicator
    // asynchronously when the tab set shrinks, and act() must see that.
    await waitFor(() => {
      // The Summary body, not an empty prompt pane.
      expect(screen.getByText("Kind")).toBeTruthy();
    });
    expect(screen.queryByText("Prompt")).toBeNull();
    expect(screen.queryByText("hello")).toBeNull();
  });
});
