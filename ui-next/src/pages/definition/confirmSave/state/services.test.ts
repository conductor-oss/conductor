import { createWorkflow, updateWorkflow } from "./services";
import { SaveWorkflowMachineContext } from "./types";
import schema from "pages/definition/EditorPanel/TaskFormTab/forms/__fixtures__/lambdaInvokeSchema.json";

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

  it.each([createWorkflow, updateWorkflow])(
    "blocks persistence when a discovered required field is missing",
    async (save) => {
      fetchWithContext.mockResolvedValue({ inputSchema: { data: schema } });
      await expect(
        save(
          {
            ...context,
            editorChanges: JSON.stringify({
              name: "invalid",
              tasks: [
                {
                  name: "invoke",
                  type: "CONNECTED_APP",
                  taskReferenceName: "invoke_ref",
                  inputParameters: {
                    integrationName: "lambda",
                    method: "invoke",
                    payload: "{}",
                  },
                },
              ],
            }),
          },
          undefined,
        ),
      ).rejects.toMatchObject({ status: 400 });
      expect(fetchWithContext).toHaveBeenCalledTimes(1);
      expect(fetchWithContext.mock.calls[0][2].method).toBe("GET");
    },
  );

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
