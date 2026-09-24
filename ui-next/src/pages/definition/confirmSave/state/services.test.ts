import { createWorkflow, updateWorkflow } from "./services";
import { SaveWorkflowMachineContext } from "./types";

const fetchWithContext = vi.hoisted(() => vi.fn());

vi.mock("plugins/fetch", () => ({
  fetchWithContext,
}));

vi.mock("utils/agentMetadata", () => ({
  resolveAgentSnapshotsInWorkflow: vi.fn(async (workflow) => workflow),
}));

const SERVER_MESSAGE =
  "taskReferenceName: nonexistent_task_ref for given task: test_simple_task input value: value of input parameter: ${nonexistent_task_ref.output.result} is not defined in workflow definition.";

const editorChanges = JSON.stringify({
  name: "wfdef28_warning_test",
  version: 1,
  tasks: [],
});

const context = {
  editorChanges,
  authHeaders: {},
  isNewVersion: false,
} as SaveWorkflowMachineContext;

function rejectionResponse(body: unknown) {
  return {
    json: async () => body,
  };
}

describe("save workflow API error parsing", () => {
  beforeEach(() => {
    fetchWithContext.mockReset();
  });

  it("createWorkflow rejects a 400 with empty validationErrors as a workflow-level error", async () => {
    fetchWithContext.mockRejectedValue(
      rejectionResponse({
        message: SERVER_MESSAGE,
        validationErrors: [],
      }),
    );

    await expect(createWorkflow(context, undefined)).rejects.toEqual({
      text: SERVER_MESSAGE,
      severity: "error",
      status: undefined,
      validationErrors: [],
    });
  });

  it("updateWorkflow rejects a 400 that omits validationErrors as a workflow-level error", async () => {
    fetchWithContext.mockRejectedValue(
      rejectionResponse({
        message: SERVER_MESSAGE,
      }),
    );

    await expect(updateWorkflow(context, undefined)).rejects.toEqual({
      text: SERVER_MESSAGE,
      severity: "error",
      status: undefined,
      validationErrors: undefined,
    });
  });
});
