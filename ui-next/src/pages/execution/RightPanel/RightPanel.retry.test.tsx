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
import { RightPanelContextEventTypes as Events } from "./state/types";
import { OUTPUT_TAB, LOGS_TAB } from "../state/constants";
import { RightPanel } from "./RightPanel";
import { fetchWithContext } from "plugins/fetch";

vi.mock("react-router", async () => import("react-router-dom"));
vi.mock("@monaco-editor/react", () => ({
  Editor: () => null,
  default: ({ value }: { value: string }) => (
    <pre data-testid="editor">{value}</pre>
  ),
}));
vi.mock("plugins/fetch", async (importOriginal) => ({
  ...(await importOriginal<typeof import("plugins/fetch")>()),
  fetchWithContext: vi.fn(),
}));
const definition = {
  name: "Invoke Function",
  taskReferenceName: "Invoke Function_ref",
  type: TaskType.CONNECTED_APP,
  inputParameters: {},
};
const attempt = (id: string, retryCount: number, overrides = {}) =>
  ({
    taskId: id,
    referenceTaskName: definition.taskReferenceName,
    workflowTask: definition,
    taskType: definition.type,
    status: TaskStatus.FAILED,
    seq: retryCount + 1,
    retryCount,
    inputData: {},
    outputData: { requestId: id, error: `error-${id}` },
    reasonForIncompletion: `reason-${id}`,
    scheduledTime: 1000,
    startTime: 1100,
    endTime: 2000,
    ...overrides,
  }) as unknown as ExecutionTask;
const services: ReturnType<typeof interpret>[] = [];
afterEach(() => {
  services.splice(0).forEach((s) => s.stop());
  vi.clearAllMocks();
});
const log = (id: string) => [
  { taskId: id, createdTime: 2000, log: `log-${id}` },
];
async function open(tasks: ExecutionTask[], selectedTaskId?: string) {
  let response = {
    workflowId: "wf",
    status: "FAILED",
    tasks,
    startTime: 1000,
    workflowDefinition: { name: "retry", version: 1, tasks: [definition] },
  };
  const fetchExecution = vi.fn(async () => response);
  vi.mocked(fetchWithContext).mockImplementation(
    async (path) => log(String(path).split("/")[2]) as any,
  );
  const service = interpret(
    executionMachine
      .withContext({
        ...executionMachine.context,
        selectedTaskId,
        selectedTaskReferenceName: selectedTaskId
          ? definition.taskReferenceName
          : undefined,
      })
      .withConfig({
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
    executionId: "wf",
  });
  await waitFor(() => expect(service.state.matches("init")).toBe(true));
  if (!selectedTaskId)
    service.send({
      type: FlowActionTypes.SELECT_NODE_EVT,
      node: {
        id: definition.taskReferenceName,
        data: {
          task: {
            ...definition,
            executionData: { status: TaskStatus.PENDING },
          },
        },
      },
    } as any);
  await waitFor(() =>
    expect(service.state.children.rightPanelMachine).toBeDefined(),
  );
  const panel = service.state.children.rightPanelMachine as any;
  render(
    <ThemeProvider theme={getTheme()}>
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <RightPanel
            rightPanelActor={panel}
            workflowName="retry"
            workflowStatus="FAILED"
          />
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  );
  return {
    service,
    panel,
    select(t: ExecutionTask) {
      act(() =>
        panel.send({ type: Events.SET_SELECTED_TASK, selectedTask: t }),
      );
    },
    tab(currentTab: number) {
      act(() => panel.send({ type: Events.CHANGE_CURRENT_TAB, currentTab }));
    },
    async refresh(tasks: ExecutionTask[]) {
      response = { ...response, tasks };
      const n = fetchExecution.mock.calls.length;
      await act(async () => service.send(ExecutionActionTypes.REFETCH));
      await waitFor(() => expect(fetchExecution).toHaveBeenCalledTimes(n + 1));
    },
  };
}
describe("retry detail identity", () => {
  it("opens the exact deep-linked attempt instead of silently selecting latest", async () => {
    const { panel } = await open(
      [attempt("original", 0), attempt("retry", 1)],
      "original",
    );
    expect(panel.state.context.selectedTask.taskId).toBe("original");
    expect(screen.getByText("reason-original")).toBeInTheDocument();
  });
  it("keeps output and error specific to the selected ID with a shared reference", async () => {
    const a = attempt("original", 0),
      b = attempt("retry", 1);
    const h = await open([a, b]);
    expect(h.panel.state.context.selectedTask.taskId).toBe("retry");
    h.tab(OUTPUT_TAB);
    expect(screen.getByTestId("editor")).toHaveTextContent("error-retry");
    h.select(a);
    expect(screen.getByTestId("editor")).toHaveTextContent("error-original");
    await h.refresh([a, b]);
    expect(h.panel.state.context.selectedTask.taskId).toBe("original");
    expect(screen.getByTestId("editor")).toHaveTextContent("error-original");
  });
  it("refreshes the same ID from scheduled inherited output to its own completed output", async () => {
    const pending = attempt("retry", 1, {
      status: TaskStatus.SCHEDULED,
      outputData: { requestId: "original" },
      startTime: 0,
      endTime: 0,
    });
    const h = await open([pending]);
    h.tab(OUTPUT_TAB);
    expect(screen.getByTestId("editor")).toHaveTextContent("original");
    await h.refresh([attempt("retry", 1)]);
    expect(screen.getByTestId("editor")).toHaveTextContent("error-retry");
    expect(screen.getByTestId("editor")).not.toHaveTextContent("original");
  });
  it("removes previous-attempt logs immediately while the selected retry loads", async () => {
    const a = attempt("original", 0),
      b = attempt("retry", 1);
    const h = await open([a, b]);
    h.select(a);
    h.tab(LOGS_TAB);
    await screen.findByText(/log-original/);
    vi.mocked(fetchWithContext).mockImplementation(() => new Promise(() => {}));
    h.select(b);
    expect(screen.queryByText(/log-original/)).not.toBeInTheDocument();
    expect(h.panel.state.context.selectedTask.taskId).toBe("retry");
  });
  it("refreshes logs for the same selected task ID", async () => {
    const a = attempt("original", 0);
    const h = await open([a]);
    h.tab(LOGS_TAB);
    await screen.findByText(/log-original/);
    vi.mocked(fetchWithContext).mockResolvedValue(log("updated") as any);
    await h.refresh([a]);
    await screen.findByText(/log-updated/);
    expect(screen.queryByText(/log-original/)).not.toBeInTheDocument();
  });
  it("ignores a late log response from an attempt selected before the current one", async () => {
    const a = attempt("original", 0),
      b = attempt("retry", 1);
    const h = await open([a, b]);
    let resolveOld!: (v: any) => void;
    vi.mocked(fetchWithContext).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOld = resolve;
        }),
    );
    h.select(a);
    h.tab(LOGS_TAB);
    await waitFor(() => expect(resolveOld).toBeDefined());
    h.select(b);
    await screen.findByText(/log-retry/);
    await act(async () => resolveOld(log("original")));
    expect(screen.queryByText(/log-original/)).not.toBeInTheDocument();
    expect(screen.getByText(/log-retry/)).toBeInTheDocument();
  });
});
