import { interpret, Interpreter } from "xstate";
import { saveMachine } from "./machine";
import {
  SaveWorkflowEvents,
  SaveWorkflowMachineContext,
  SaveWorkflowMachineEventTypes,
} from "./types";
import { ErrorInspectorEventTypes } from "../../errorInspector/state";

const SERVER_MESSAGE =
  "taskReferenceName: nonexistent_task_ref for given task: test_simple_task input value: value of input parameter: ${nonexistent_task_ref.output.result} is not defined in workflow definition.";

const workflowJson = JSON.stringify(
  {
    name: "wfdef28_warning_test",
    version: 1,
    tasks: [
      {
        name: "test_simple_task",
        taskReferenceName: "test_simple_task_ref",
        type: "SIMPLE",
        inputParameters: {
          value: "${nonexistent_task_ref.output.result}",
        },
      },
    ],
  },
  null,
  2,
);

function waitForState(
  service: Interpreter<SaveWorkflowMachineContext, any, SaveWorkflowEvents>,
  predicate: (state: typeof service.state) => boolean,
): Promise<typeof service.state> {
  return new Promise((resolve) => {
    if (predicate(service.state)) {
      resolve(service.state);
      return;
    }
    const sub = service.subscribe((state) => {
      if (predicate(state)) {
        sub.unsubscribe();
        resolve(state);
      }
    });
  });
}

describe("saveMachine server validation errors", () => {
  it("reports a 400 with empty validationErrors to the error inspector", async () => {
    const reported: Array<{
      type: string;
      text?: string;
      validationErrors?: unknown;
    }> = [];
    const errorInspectorMachine = {
      send: (event: {
        type: string;
        text?: string;
        validationErrors?: unknown;
      }) => {
        reported.push(event);
      },
    };

    const machine = saveMachine
      .withContext({
        currentWf: { name: "wfdef28_warning_test", version: 1 },
        editorChanges: workflowJson,
        isNewWorkflow: false,
        workflowName: "wfdef28_warning_test",
        errorInspectorMachine: errorInspectorMachine as any,
        authHeaders: {},
        currentVersion: 1,
        isNewVersion: undefined,
        isContinueCreate: undefined,
      })
      .withConfig({
        services: {
          resolveAgentSnapshots: async ({ editorChanges }) => editorChanges,
          updateWorkflow: async () =>
            Promise.reject({
              text: SERVER_MESSAGE,
              severity: "error",
              validationErrors: [],
            }),
        },
      });

    const service = interpret(machine, {
      parent: { send: () => undefined } as any,
    }).start();

    service.send({ type: SaveWorkflowMachineEventTypes.CONFIRM_SAVE_EVT });

    await waitForState(service, (state) => state.matches("savedCancelled"));

    expect(reported).toEqual([
      {
        type: ErrorInspectorEventTypes.REPORT_SERVER_ERROR,
        text: SERVER_MESSAGE,
        validationErrors: [],
      },
    ]);

    service.stop();
  });
});
