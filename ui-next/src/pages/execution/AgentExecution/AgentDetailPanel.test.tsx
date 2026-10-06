import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { pluginRegistry } from "plugins/registry";

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

describe("AgentDetailPanel plugin task panels", () => {
  const task = {
    taskId: "t-1",
    taskType: "LLM_CHAT_COMPLETE",
    inputData: { guardrails: ["pii"] },
  };

  function withTask(node: DetailNodeData): DetailNodeData {
    return { ...node, event: { ...node.event!, task: task as any } };
  }

  function registerPanel(shouldShow?: (taskResult: any) => boolean) {
    return vi.spyOn(pluginRegistry, "getTaskExecutionPanels").mockReturnValue([
      {
        id: "llm-guardrail-executions",
        label: "Guardrails",
        taskTypes: ["LLM_CHAT_COMPLETE"],
        component: ({ taskResult }) => (
          <div>guardrails for {taskResult.taskId}</div>
        ),
        shouldShow,
      },
    ]);
  }

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("shows a registered panel for the LLM call's task", () => {
    const panels = registerPanel();
    renderPanel(withTask(llmNode({})));

    expect(panels).toHaveBeenCalledWith("LLM_CHAT_COMPLETE");
    fireEvent.click(screen.getByText("Guardrails"));
    expect(screen.getByText("guardrails for t-1")).toBeTruthy();
  });

  it("respects the panel's shouldShow predicate", () => {
    registerPanel(() => false);
    renderPanel(withTask(llmNode({})));

    expect(screen.queryByText("Guardrails")).toBeNull();
  });

  it("shows no plugin panel without the task or for non-LLM nodes", () => {
    registerPanel();
    const { unmount } = renderPanel(llmNode({}));
    expect(screen.queryByText("Guardrails")).toBeNull();
    unmount();

    renderPanel({ ...withTask(llmNode({})), kind: "tool" });
    expect(screen.queryByText("Guardrails")).toBeNull();
  });
});

describe("AgentDetailPanel LLM failure", () => {
  it("shows why the LLM call failed", () => {
    const node = llmNode({});
    renderPanel({
      ...node,
      status: AgentStatus.FAILED,
      event: {
        ...node.event!,
        task: {
          taskId: "t-1",
          taskType: "LLM_CHAT_COMPLETE",
          reasonForIncompletion: "guardrail block at USER_MESSAGE",
        } as any,
      },
    });

    expect(screen.getByText("Failure")).toBeTruthy();
    expect(screen.getByText("guardrail block at USER_MESSAGE")).toBeTruthy();
  });
});
