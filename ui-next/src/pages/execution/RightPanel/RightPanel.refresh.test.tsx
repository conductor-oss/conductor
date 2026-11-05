import { act, render, screen, waitFor } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import { getTheme } from "theme/theme";
import { QueryClient, QueryClientProvider } from "react-query";
import { MemoryRouter } from "react-router-dom";
import { interpret } from "xstate";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ExecutionTask, TaskStatus, TaskType } from "types";
import { FlowActionTypes } from "components/features/flow/state/types";
import { executionMachine } from "../state/machine";
import { ExecutionActionTypes } from "../state/types";
import { RightPanelContextEventTypes } from "./state/types";
import { RightPanel } from "./RightPanel";

// Match the router peer used by the linked workspace, as in TaskSearch tests.
vi.mock("react-router", async () => import("react-router-dom"));
vi.mock("@monaco-editor/react", () => ({
  default: () => null,
  Editor: () => null,
}));

const workflowId = "0808dc81-d8fd-11f1-9591-2653c9955b17";
const taskId = "0ad8c563-d8fd-11f1-9591-2653c9955b17";
const definition = {
  name: "Invoke Function",
  taskReferenceName: "Invoke Function_ref",
  type: TaskType.CONNECTED_APP,
  inputParameters: {},
};
const task = (status: TaskStatus, overrides = {}) =>
  ({
    taskId,
    referenceTaskName: definition.taskReferenceName,
    workflowTask: definition,
    taskType: definition.type,
    status,
    seq: 2,
    retryCount: 0,
    scheduledTime: 1000,
    startTime: 1100,
    endTime: 2000,
    ...overrides,
  }) as unknown as ExecutionTask;
const execution = (tasks: ExecutionTask[], status = "COMPLETED") => ({
  workflowId,
  status,
  tasks,
  startTime: 1000,
  workflowDefinition: {
    name: "NewWorkflow_165ce",
    version: 1,
    tasks: [definition],
  },
});
const services: ReturnType<typeof interpret>[] = [];
afterEach(() => services.splice(0).forEach((service) => service.stop()));

async function openPendingGraphTask(type = TaskType.CONNECTED_APP) {
  const selectedDefinition = { ...definition, type };
  let response = execution([], "PAUSED");
  const fetchExecution = vi.fn(async () => response);
  const service = interpret(
    executionMachine.withConfig({
      services: { fetchExecution } as any,
      actions: {
        setQueryParam: () => {},
        setTaskIdQueryParam: () => {},
        notifyOnHumanTask: () => {},
        gtagEventLogger: () => {},
        startRenderingGtag: () => {},
      },
    }),
  );
  services.push(service as any);
  service.start();
  service.send({
    type: ExecutionActionTypes.UPDATE_EXECUTION,
    executionId: workflowId,
  });
  await waitFor(() => expect(service.state.matches("init")).toBe(true));
  service.send({
    type: FlowActionTypes.SELECT_NODE_EVT,
    node: {
      id: definition.taskReferenceName,
      data: {
        task: {
          ...selectedDefinition,
          executionData: { status: TaskStatus.PENDING },
        },
      },
    },
  } as any);
  const panel = service.state.children.rightPanelMachine as any;
  render(
    <ThemeProvider theme={getTheme()}>
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <RightPanel
            rightPanelActor={panel}
            workflowName="NewWorkflow_165ce"
            workflowStatus="COMPLETED"
          />
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  );
  expect(panel.state.context.selectedTask.taskId).toBeUndefined();
  expect(screen.getAllByText("Pending")).toHaveLength(2);
  return {
    service,
    panel,
    fetchExecution,
    async refresh(tasks: ExecutionTask[], status?: string) {
      response = execution(tasks, status);
      const count = fetchExecution.mock.calls.length;
      await act(async () => service.send(ExecutionActionTypes.REFETCH));
      await waitFor(() =>
        expect(fetchExecution).toHaveBeenCalledTimes(count + 1),
      );
    },
  };
}

describe("graph task drawer refresh", () => {
  it("hydrates a definition-only Pending selection on top Refresh when the graph is completed", async () => {
    const { panel, refresh } = await openPendingGraphTask();
    await refresh([task(TaskStatus.COMPLETED)]);
    expect(panel.state.context.selectedTask.taskId).toBe(taskId);
    expect(screen.queryByText("Pending")).not.toBeInTheDocument();
    expect(screen.getAllByText("Completed")).toHaveLength(2);
    expect(screen.getByText("Task execution id")).toBeInTheDocument();
    expect(screen.getAllByText(taskId)).toHaveLength(2);
  });

  it.each([TaskStatus.COMPLETED, TaskStatus.FAILED])(
    "updates Pending -> IN_PROGRESS -> %s using the task record",
    async (terminal) => {
      const { panel, refresh } = await openPendingGraphTask();
      await refresh([task(TaskStatus.IN_PROGRESS)], "RUNNING");
      expect(panel.state.context.selectedTask.status).toBe(
        TaskStatus.IN_PROGRESS,
      );
      await refresh([task(terminal)]);
      expect(panel.state.context.selectedTask.status).toBe(terminal);
      expect(screen.queryByText("Pending")).not.toBeInTheDocument();
    },
  );

  it.each([TaskType.SIMPLE, TaskType.HTTP])(
    "hydrates an unscheduled %s node through the same model contract",
    async (type) => {
      const { panel, refresh } = await openPendingGraphTask(type);
      await refresh([
        task(TaskStatus.COMPLETED, {
          taskType: type,
          workflowTask: { ...definition, type },
        }),
      ]);
      expect(panel.state.context.selectedTask.status).toBe(
        TaskStatus.COMPLETED,
      );
      expect(panel.state.context.selectedTask.workflowTask.type).toBe(type);
      expect(screen.queryByText("Pending")).not.toBeInTheDocument();
    },
  );

  it("retains an explicitly selected attempt if a partial response omits it", async () => {
    const { panel, refresh } = await openPendingGraphTask();
    const oldAttempt = task(TaskStatus.FAILED);
    act(() =>
      panel.send({
        type: RightPanelContextEventTypes.SET_SELECTED_TASK,
        selectedTask: oldAttempt,
      }),
    );
    await refresh([task(TaskStatus.COMPLETED, { taskId: "retry-id", seq: 3 })]);
    expect(panel.state.context.selectedTask).toBe(oldAttempt);
  });

  it("keeps a truly unscheduled node Pending, even in a terminal workflow", async () => {
    const { panel, refresh } = await openPendingGraphTask();
    await refresh([
      task(TaskStatus.COMPLETED, {
        taskId: "other",
        referenceTaskName: "other_ref",
        workflowTask: { ...definition, taskReferenceName: "other_ref" },
      }),
    ]);
    expect(panel.state.context.selectedTask.taskId).toBeUndefined();
    expect(screen.getAllByText("Pending")).toHaveLength(2);
    expect(screen.queryByText("Task execution id")).not.toBeInTheDocument();
  });

  it("preserves an explicitly selected old retry during refresh", async () => {
    const { panel, refresh } = await openPendingGraphTask();
    const oldAttempt = task(TaskStatus.FAILED);
    act(() =>
      panel.send({
        type: RightPanelContextEventTypes.SET_SELECTED_TASK,
        selectedTask: oldAttempt,
      }),
    );
    await refresh([
      oldAttempt,
      task(TaskStatus.COMPLETED, { taskId: "retry-id", seq: 3, retryCount: 1 }),
    ]);
    expect(panel.state.context.selectedTask.taskId).toBe(taskId);
    expect(panel.state.context.selectedTask.status).toBe(TaskStatus.FAILED);
  });

  it("uses the latest instance for a reference-only selection with repeated references", async () => {
    const { panel, refresh } = await openPendingGraphTask();
    await refresh([
      task(TaskStatus.FAILED),
      task(TaskStatus.COMPLETED, { taskId: "retry-id", seq: 3, retryCount: 1 }),
    ]);
    expect(panel.state.context.selectedTask.taskId).toBe("retry-id");
  });
});
