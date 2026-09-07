import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ReactNode, useState } from "react";
import { TaskDef, TaskType } from "types";
import { AgentTaskForm } from "./AgentTaskForm";

const fetchWithContext = vi.hoisted(() => vi.fn());

vi.mock("plugins/fetch", () => ({
  fetchWithContext,
  fetchContextNonHook: () => ({}),
  useFetchContext: () => ({}),
}));

vi.mock("utils/query", () => ({
  useAuthHeaders: () => ({ "X-Authorization": "ui-token" }),
  useFetch: () => ({ data: [{ name: "researcher", version: 2 }] }),
}));

vi.mock("components/FlatMapForm/ConductorAutocompleteVariables", () => ({
  ConductorAutocompleteVariables: ({ label, value, onChange, onBlur }: any) => (
    <input
      aria-label={String(label)}
      value={value ?? ""}
      onChange={(event) => onChange(event.target.value)}
      onBlur={() => onBlur?.(String(value ?? ""))}
    />
  ),
}));

vi.mock("components/ui/inputs/RadioButtonGroup", () => ({
  default: ({ name, value, onChange, items }: any) => (
    <select aria-label={name} value={value} onChange={onChange}>
      {items.map((item: any) => (
        <option key={item.value} value={item.value}>
          {item.label}
        </option>
      ))}
    </select>
  ),
}));

vi.mock("components/ui/inputs/ConductorInput", () => ({
  default: ({ label, value, onTextInputChange }: any) => (
    <textarea
      aria-label={label}
      value={value ?? ""}
      onChange={(event) => onTextInputChange?.(event.target.value)}
    />
  ),
}));

vi.mock("components/ui/inputs/ConductorCodeBlockInput", () => ({
  ConductorCodeBlockInput: () => null,
}));
vi.mock("components/FlatMapForm/ConductorFlatMapForm", () => ({
  ConductorFlatMapFormBase: () => null,
}));
vi.mock("./HTTPTaskForm/ConductorAdditionalHeaders", () => ({
  ConductorAdditionalHeadersBase: () => null,
}));
vi.mock("./ConductorCacheOutputForm", () => ({
  ConductorCacheOutput: () => null,
}));
vi.mock("./OptionalFieldForm", () => ({ Optional: () => null }));
vi.mock("./TaskFormSection", () => ({
  default: ({ title, children }: { title?: string; children: ReactNode }) => (
    <section aria-label={title}>{children}</section>
  ),
}));
vi.mock("components/features/agents/AgentSnapshotDetails", () => ({
  AgentSnapshotDetails: ({ snapshot }: any) => (
    <div>Snapshot: {snapshot.displayName}</div>
  ),
}));

function Harness({ initialTask }: { initialTask: Partial<TaskDef> }) {
  const [task, setTask] = useState(initialTask);
  return (
    <>
      <AgentTaskForm task={task} onChange={setTask} />
      <pre data-testid="task-json">{JSON.stringify(task)}</pre>
    </>
  );
}

const savedTask = (): Partial<TaskDef> =>
  JSON.parse(screen.getByTestId("task-json").textContent ?? "{}");

describe("AgentTaskForm metadata resolution", () => {
  beforeEach(() => {
    fetchWithContext.mockReset();
  });

  it("automatically resolves a selected Conductor agent", async () => {
    fetchWithContext.mockResolvedValue({
      name: "researcher",
      version: 7,
      metadata: {
        agent_sdk: "openai-agents",
        normalizedAgentDef: {
          name: "Research Agent",
          model: "openai/gpt-5",
        },
      },
    });

    render(
      <Harness
        initialTask={{
          name: "agent",
          taskReferenceName: "agent_ref",
          type: TaskType.AGENT,
          inputParameters: {
            agentType: "conductor",
            name: "researcher",
            version: 2,
          },
        }}
      />,
    );

    await waitFor(() =>
      expect(savedTask().metadata?.agent).toMatchObject({
        resolved: true,
        displayName: "Research Agent",
        conductor: {
          requestedVersion: 2,
          resolvedVersion: 7,
          framework: "openai-agents",
          agentConfig: { model: "openai/gpt-5" },
        },
      }),
    );
    expect(fetchWithContext).toHaveBeenCalledWith(
      "/agent/definitions/researcher?version=2",
      expect.anything(),
      expect.objectContaining({
        headers: expect.objectContaining({
          "X-Authorization": "ui-token",
        }),
      }),
    );
  });

  it("resolves A2A on URL blur, refreshes, and warns without blocking on failure", async () => {
    fetchWithContext.mockResolvedValueOnce({
      agentCard: { name: "Travel Agent", version: "1.0" },
    });
    render(
      <Harness
        initialTask={{
          name: "agent",
          taskReferenceName: "agent_ref",
          type: TaskType.AGENT,
          inputParameters: {
            agentType: "a2a",
            agentUrl: "https://agent.example",
            headers: { Authorization: "Bearer remote-secret" },
          },
        }}
      />,
    );

    fireEvent.blur(screen.getByLabelText("Agent URL"));
    await waitFor(() =>
      expect(savedTask().metadata?.agent).toMatchObject({
        resolved: true,
        displayName: "Travel Agent",
      }),
    );
    expect(JSON.stringify(savedTask().metadata?.agent)).not.toContain(
      "remote-secret",
    );

    fetchWithContext.mockRejectedValueOnce(new Error("offline"));
    fireEvent.click(
      screen.getByRole("button", { name: "Refresh agent details" }),
    );
    expect(
      await screen.findByText(/Agent Card could not be resolved/),
    ).toBeInTheDocument();
    expect(savedTask().metadata?.agent).toMatchObject({
      resolved: false,
      source: { url: "https://agent.example" },
    });
  });

  it("invalidates changed sources and never resolves dynamic expressions", async () => {
    render(
      <Harness
        initialTask={{
          name: "agent",
          taskReferenceName: "agent_ref",
          type: TaskType.AGENT,
          inputParameters: {
            agentType: "a2a",
            agentUrl: "https://old.example",
          },
          metadata: {
            agent: {
              schemaVersion: 1,
              agentType: "a2a",
              displayName: "Old Agent",
              source: { url: "https://old.example" },
              resolved: true,
              a2a: {
                url: "https://old.example",
                agentCard: { name: "Old Agent" },
              },
            },
          },
        }}
      />,
    );

    fireEvent.change(screen.getByLabelText("Agent URL"), {
      target: { value: "${workflow.input.agentUrl}" },
    });
    expect(savedTask().metadata?.agent).toMatchObject({
      resolved: false,
      displayName: "${workflow.input.agentUrl}",
      source: {
        url: "${workflow.input.agentUrl}",
        expression: "${workflow.input.agentUrl}",
      },
    });

    fireEvent.blur(screen.getByLabelText("Agent URL"));
    await waitFor(() => expect(fetchWithContext).not.toHaveBeenCalled());
  });
});
