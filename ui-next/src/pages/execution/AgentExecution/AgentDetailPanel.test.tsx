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
});
