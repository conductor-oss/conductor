import { act, renderHook, waitFor } from "@testing-library/react";
import {
  BrowserRouter,
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { QueryClient, QueryClientProvider } from "react-query";
import { PropsWithChildren } from "react";
import { describe, expect, it, vi } from "vitest";
import { useExecutionMachine } from "./hook";

vi.mock("react-router", async () => import("react-router-dom"));
vi.mock("@monaco-editor/react", () => ({
  default: () => null,
  Editor: () => null,
}));
vi.mock("utils/query", async (importOriginal) => ({
  ...(await importOriginal<typeof import("utils/query")>()),
  useAuthHeaders: () => ({}),
  useCurrentUserInfo: () => ({ data: undefined }),
}));
vi.mock("./machine", async (importOriginal) => {
  const { executionMachine } =
    await importOriginal<typeof import("./machine")>();
  const definition = {
    name: "Invoke Function",
    taskReferenceName: "Invoke Function_ref",
    type: "CONNECTED_APP",
    inputParameters: {},
  };
  const tasks = ["original", "retry"].map((taskId, retryCount) => ({
    taskId,
    retryCount,
    seq: retryCount + 1,
    referenceTaskName: definition.taskReferenceName,
    workflowTask: definition,
    taskType: definition.type,
    status: "FAILED",
    scheduledTime: 1000,
    startTime: 1100,
    endTime: 2000,
    outputData: { requestId: taskId },
    reasonForIncompletion: `error-${taskId}`,
  }));
  return {
    executionMachine: executionMachine.withConfig({
      services: {
        fetchExecution: async () => ({
          workflowId: "wf",
          status: "FAILED",
          tasks,
          workflowDefinition: {
            name: "retry",
            version: 1,
            tasks: [
              definition,
              {
                name: "unscheduled",
                taskReferenceName: "unscheduled_ref",
                type: "SIMPLE",
                inputParameters: {},
              },
            ],
          },
        }),
      } as any,
    }),
  };
});
function wrapper({ children }: PropsWithChildren) {
  return (
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={["/execution/wf"]}>
        <Routes>
          <Route path="/execution/:id" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}
describe("timeline task selection", () => {
  it("chooses latest only for reference-only selection, never as fallback for an unknown explicit ID", async () => {
    const { result } = renderHook(useExecutionMachine, { wrapper });
    await waitFor(() => expect(result.current[1].isReady).toBe(true));
    act(() => result.current[0].selectTask({ ref: "Invoke Function_ref" }));
    await waitFor(() =>
      expect(result.current[1].rightPanelActor).toBeDefined(),
    );
    const panel = result.current[1].rightPanelActor as any;
    expect(panel.getSnapshot().context.selectedTask.taskId).toBe("retry");
    act(() =>
      result.current[0].selectTask({
        ref: "Invoke Function_ref",
        taskId: "missing",
      }),
    );
    await waitFor(() =>
      expect(result.current[1].rightPanelActor).toBeUndefined(),
    );
  });
  it("prefers the clicked retry ID over the same-reference original attempt", async () => {
    const { result } = renderHook(
      () => ({ machine: useExecutionMachine(), location: useLocation() }),
      { wrapper },
    );
    await waitFor(() => expect(result.current.machine[1].isReady).toBe(true));
    act(() =>
      result.current.machine[0].selectTask({
        ref: "Invoke Function_ref",
        taskId: "retry",
      }),
    );
    await waitFor(() =>
      expect(result.current.machine[1].rightPanelActor).toBeDefined(),
    );
    const panel = result.current.machine[1].rightPanelActor as any;
    expect(panel.getSnapshot().context.selectedTask.taskId).toBe("retry");
    expect(panel.getSnapshot().context.selectedTask.outputData.requestId).toBe(
      "retry",
    );
    expect(panel.getSnapshot().context.selectedTask.reasonForIncompletion).toBe(
      "error-retry",
    );
    expect(
      new URLSearchParams(result.current.location.search).get("taskId"),
    ).toBe("retry");
    expect(
      new URLSearchParams(result.current.location.search).get(
        "taskReferenceName",
      ),
    ).toBe("Invoke Function_ref");
    await act(async () => result.current.machine[0].refetch());
    await waitFor(() =>
      expect(panel.getSnapshot().context.selectedTask.taskId).toBe("retry"),
    );
    act(() =>
      result.current.machine[0].selectTask({
        ref: "Invoke Function_ref",
        taskId: "original",
      }),
    );
    expect(panel.getSnapshot().context.selectedTask.taskId).toBe("original");
    expect(
      new URLSearchParams(result.current.location.search).get("taskId"),
    ).toBe("original");
  });
});

const selected = (result: any) =>
  result.current.machine[1].rightPanelActor?.getSnapshot().context.selectedTask
    .taskId;
const query = (result: any, field = "taskId") =>
  new URLSearchParams(result.current.location.search).get(field);
const useHarness = () => ({
  machine: useExecutionMachine(),
  location: useLocation(),
  navigate: useNavigate(),
});
const withEntries =
  (entries: string[]) =>
  ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={entries}>
        <Routes>
          <Route path="/execution/:id" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
const originalUrl =
  "/execution/wf?taskId=original&taskReferenceName=Invoke+Function_ref&tab=timeLine&keep=yes#detail";
const retryUrl =
  "/execution/wf?taskId=retry&taskReferenceName=Invoke+Function_ref&tab=timeLine&keep=yes#detail";

describe("selection query and router consistency", () => {
  it("follows Back and Forward without replacing history entries or reopening a cleared selection", async () => {
    const { result } = renderHook(useHarness, {
      wrapper: withEntries(["/execution/wf?keep=yes", originalUrl, retryUrl]),
    });
    await waitFor(() => expect(selected(result)).toBe("retry"));
    const retryKey = result.current.location.key;
    act(() => result.current.navigate(-1));
    await waitFor(() => expect(selected(result)).toBe("original"));
    const originalKey = result.current.location.key;
    expect(query(result)).toBe("original");
    act(() => result.current.navigate(-1));
    await waitFor(() =>
      expect(result.current.machine[1].rightPanelActor).toBeUndefined(),
    );
    expect(query(result)).toBeNull();
    expect(query(result, "taskReferenceName")).toBeNull();
    act(() => result.current.navigate(1));
    await waitFor(() => expect(selected(result)).toBe("original"));
    expect(result.current.location.key).toBe(originalKey);
    act(() => result.current.navigate(1));
    await waitFor(() => expect(selected(result)).toBe("retry"));
    expect(result.current.location.key).toBe(retryKey);
    await act(async () => result.current.machine[0].refetch());
    await waitFor(() => expect(result.current.machine[1].isReady).toBe(true));
    expect(selected(result)).toBe("retry");
    expect(result.current.location.key).toBe(retryKey);
    expect(query(result, "keep")).toBe("yes");
    expect(result.current.location.hash).toBe("#detail");
  });
  it.each(["original", "retry"])(
    "opens direct task-ID-only deep link %s and keeps it pinned on refresh",
    async (id) => {
      const { result } = renderHook(useHarness, {
        wrapper: withEntries([`/execution/wf?taskId=${id}&keep=yes`]),
      });
      await waitFor(() => expect(selected(result)).toBe(id));
      await act(async () => result.current.machine[0].refetch());
      await waitFor(() => expect(result.current.machine[1].isReady).toBe(true));
      expect(selected(result)).toBe(id);
      expect(query(result)).toBe(id);
      expect(query(result, "keep")).toBe("yes");
    },
  );
  it("reconciles query-only navigation and reference-only selection", async () => {
    const { result } = renderHook(useHarness, {
      wrapper: withEntries([retryUrl]),
    });
    await waitFor(() => expect(selected(result)).toBe("retry"));
    act(() =>
      result.current.navigate(
        "?taskId=original&taskReferenceName=Invoke+Function_ref",
      ),
    );
    await waitFor(() => expect(selected(result)).toBe("original"));
    act(() =>
      result.current.navigate("?taskReferenceName=Invoke+Function_ref"),
    );
    await waitFor(() => expect(selected(result)).toBe("retry"));
    expect(query(result)).toBe("retry");
  });
  it.each(["missing", ""])(
    "does not fall back from explicit ID %j and clears both query fields",
    async (id) => {
      const { result } = renderHook(useHarness, {
        wrapper: withEntries([retryUrl]),
      });
      await waitFor(() => expect(selected(result)).toBe("retry"));
      act(() =>
        result.current.machine[0].selectTask({
          ref: "Invoke Function_ref",
          taskId: id,
        }),
      );
      await waitFor(() =>
        expect(result.current.machine[1].rightPanelActor).toBeUndefined(),
      );
      expect(query(result)).toBeNull();
      expect(query(result, "taskReferenceName")).toBeNull();
      expect(query(result, "keep")).toBe("yes");
    },
  );
  it.each([
    "?taskId=missing&taskReferenceName=Invoke+Function_ref",
    "?taskId=missing",
    "?taskId=missing&taskReferenceName=",
    "?taskId=&taskReferenceName=Invoke+Function_ref",
  ])(
    "clears invalid explicit deep link %s without reference fallback",
    async (search) => {
      const { result } = renderHook(useHarness, {
        wrapper: withEntries([`/execution/wf${search}&keep=yes`]),
      });
      await waitFor(() => expect(result.current.machine[1].isReady).toBe(true));
      await waitFor(() => expect(query(result)).toBeNull());
      expect(result.current.machine[1].rightPanelActor).toBeUndefined();
      expect(query(result, "taskReferenceName")).toBeNull();
      expect(query(result, "keep")).toBe("yes");
    },
  );
  it("closes both drawer and URL on explicit close and invalid query navigation", async () => {
    const { result } = renderHook(useHarness, {
      wrapper: withEntries([retryUrl]),
    });
    await waitFor(() => expect(selected(result)).toBe("retry"));
    act(() => result.current.machine[0].closeRightPanel());
    await waitFor(() =>
      expect(result.current.machine[1].rightPanelActor).toBeUndefined(),
    );
    expect(query(result)).toBeNull();
    expect(query(result, "taskReferenceName")).toBeNull();
    act(() => result.current.navigate("?taskId=original"));
    await waitFor(() => expect(selected(result)).toBe("original"));
    act(() =>
      result.current.navigate(
        "?taskId=missing&taskReferenceName=Invoke+Function_ref",
      ),
    );
    await waitFor(() =>
      expect(result.current.machine[1].rightPanelActor).toBeUndefined(),
    );
    expect(query(result)).toBeNull();
    expect(query(result, "taskReferenceName")).toBeNull();
  });
  it("ignores unrelated query changes and repeated exact selection without navigation loops", async () => {
    const { result } = renderHook(useHarness, {
      wrapper: withEntries([retryUrl]),
    });
    await waitFor(() => expect(selected(result)).toBe("retry"));
    const actor = result.current.machine[1].rightPanelActor;
    const key = result.current.location.key;
    act(() =>
      result.current.machine[0].selectTask({
        taskId: "retry",
        ref: "Invoke Function_ref",
      }),
    );
    expect(result.current.location.key).toBe(key);
    act(() =>
      result.current.navigate(
        "?taskId=retry&taskReferenceName=Invoke+Function_ref&keep=changed",
      ),
    );
    const nextKey = result.current.location.key;
    await act(async () => result.current.machine[0].refetch());
    await waitFor(() => expect(result.current.machine[1].isReady).toBe(true));
    expect(result.current.machine[1].rightPanelActor).toBe(actor);
    expect(selected(result)).toBe("retry");
    expect(result.current.location.key).toBe(nextKey);
  });
  it("handles a browser popstate on an already mounted execution", async () => {
    const priorUrl = window.location.href;
    window.history.replaceState({}, "", retryUrl);
    const browserWrapper = ({ children }: PropsWithChildren) => (
      <QueryClientProvider client={new QueryClient()}>
        <BrowserRouter>
          <Routes>
            <Route path="/execution/:id" element={children} />
          </Routes>
        </BrowserRouter>
      </QueryClientProvider>
    );
    const { result, unmount } = renderHook(useHarness, {
      wrapper: browserWrapper,
    });
    try {
      await waitFor(() => expect(selected(result)).toBe("retry"));
      act(() => {
        window.history.replaceState({ key: "popped", idx: 0 }, "", originalUrl);
        window.dispatchEvent(
          new PopStateEvent("popstate", { state: window.history.state }),
        );
      });
      await waitFor(() => expect(selected(result)).toBe("original"));
      expect(query(result)).toBe("original");
      expect(result.current.location.key).toBe("popped");
    } finally {
      unmount();
      window.history.replaceState({}, "", priorUrl);
    }
  });
  it("keeps reference-only unscheduled nodes Pending when replacing an explicit attempt", async () => {
    const { result } = renderHook(useHarness, {
      wrapper: withEntries([retryUrl]),
    });
    await waitFor(() => expect(selected(result)).toBe("retry"));
    act(() => result.current.navigate("?taskReferenceName=unscheduled_ref"));
    await waitFor(() =>
      expect(
        (result.current.machine[1].rightPanelActor as any)?.getSnapshot()
          .context.selectedTask.workflowTask.taskReferenceName,
      ).toBe("unscheduled_ref"),
    );
    expect(
      (result.current.machine[1].rightPanelActor as any).getSnapshot().context
        .selectedTask.status,
    ).toBe("PENDING");
    expect(query(result)).toBeNull();
    expect(query(result, "taskReferenceName")).toBe("unscheduled_ref");
  });
  it("cancels queued reference selection when navigation clears the drawer", async () => {
    const { result } = renderHook(useHarness, {
      wrapper: withEntries([retryUrl]),
    });
    await waitFor(() => expect(selected(result)).toBe("retry"));
    act(() =>
      result.current.navigate("?taskReferenceName=Invoke+Function_ref"),
    );
    act(() => result.current.navigate("?keep=yes"));
    await waitFor(() =>
      expect(result.current.machine[1].rightPanelActor).toBeUndefined(),
    );
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 200));
    });
    expect(result.current.machine[1].rightPanelActor).toBeUndefined();
    expect(query(result)).toBeNull();
    expect(query(result, "taskReferenceName")).toBeNull();
  });
  it("clears a prior task ID when a truly unscheduled graph node is clicked", async () => {
    const { result } = renderHook(useHarness, {
      wrapper: withEntries([retryUrl]),
    });
    await waitFor(() => expect(selected(result)).toBe("retry"));
    await waitFor(() =>
      expect(
        result.current.machine[1].nodes.some(
          (node) => node.id === "unscheduled_ref",
        ),
      ).toBe(true),
    );
    act(() =>
      result.current.machine[0].selectNode(
        result.current.machine[1].nodes.find(
          (node) => node.id === "unscheduled_ref",
        )!,
      ),
    );
    await waitFor(() => expect(query(result)).toBeNull());
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 200));
    });
    expect(
      (result.current.machine[1].rightPanelActor as any).getSnapshot().context
        .selectedTask.status,
    ).toBe("PENDING");
    expect(query(result, "taskReferenceName")).toBe("unscheduled_ref");
  });
  it("cleans query fields when the drawer child completes", async () => {
    const { result } = renderHook(useHarness, {
      wrapper: withEntries([retryUrl]),
    });
    await waitFor(() => expect(selected(result)).toBe("retry"));
    act(() =>
      result.current.machine[1].rightPanelActor!.send({
        type: "CLOSE_RIGHT_PANEL",
      }),
    );
    await waitFor(() =>
      expect(result.current.machine[1].rightPanelActor).toBeUndefined(),
    );
    expect(query(result)).toBeNull();
    expect(query(result, "taskReferenceName")).toBeNull();
  });
});
