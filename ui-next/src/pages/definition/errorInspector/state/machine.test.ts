import { interpret, Interpreter } from "xstate";
import { errorInspectorMachine } from "./machine";
import {
  ErrorInspectorEventTypes,
  ErrorInspectorMachineContext,
  ErrorInspectorMachineEvents,
} from "./types";
import { TaskDef, TaskType } from "types";

const SERVER_MESSAGE =
  "taskReferenceName: nonexistent_task_ref for given task: test_simple_task input value: value of input parameter: ${nonexistent_task_ref.output.result} is not defined in workflow definition.";

const danglingRefTask: TaskDef = {
  name: "test_simple_task",
  taskReferenceName: "test_simple_task_ref",
  type: TaskType.SIMPLE,
  startDelay: 0,
  joinOn: [],
  defaultExclusiveJoinTask: [],
  optional: false,
  asyncComplete: false,
  description: "",
  inputParameters: {
    value: "${nonexistent_task_ref.output.result}",
  },
};

const workflow = {
  name: "wfdef28_warning_test",
  version: 1,
  tasks: [danglingRefTask],
};

function waitForState(
  service: Interpreter<
    ErrorInspectorMachineContext,
    any,
    ErrorInspectorMachineEvents
  >,
  predicate: (
    state: Interpreter<
      ErrorInspectorMachineContext,
      any,
      ErrorInspectorMachineEvents
    >["state"],
  ) => boolean,
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

function startInspector() {
  const machine = errorInspectorMachine.withConfig({
    services: {
      fetchSecretsEndEnvironmentsList: async () => ({ secrets: [], envs: {} }),
    },
    actions: {
      cleanImportSummary: () => undefined,
    } as any,
  });
  const service = interpret(machine, {
    parent: { send: () => undefined } as any,
  }).start();
  return service;
}

describe("errorInspectorMachine server errors and missing references", () => {
  it("keeps a 400 with empty validationErrors after the flow re-renders, and still reports missing-reference warnings", async () => {
    const service = startInspector();

    await waitForState(service, (state) => state.matches("errorsDisplay"));

    service.send({
      type: ErrorInspectorEventTypes.SET_WORKFLOW,
      workflow,
    });
    service.send({
      type: ErrorInspectorEventTypes.REPORT_SERVER_ERROR,
      text: SERVER_MESSAGE,
      validationErrors: [],
    });

    expect(service.state.context.serverErrors).toHaveLength(1);
    expect(service.state.context.serverErrors[0].message).toBe(SERVER_MESSAGE);
    expect(
      service.state.context.serverErrors[0].validationErrors,
    ).toBeUndefined();
    expect(service.state.context.expanded).toBe(true);

    service.send({
      type: ErrorInspectorEventTypes.FLOW_FINISHED_RENDERING,
      nodes: [
        {
          id: danglingRefTask.taskReferenceName,
          data: {
            task: danglingRefTask,
            crumbs: [],
            selected: false,
          },
        },
      ],
    });

    const afterRender = await waitForState(service, (state) =>
      state.matches("errorsDisplay.missingReferences.referencesMenus"),
    );

    expect(afterRender.context.serverErrors).toHaveLength(1);
    expect(afterRender.context.serverErrors[0].message).toBe(SERVER_MESSAGE);
    expect(afterRender.context.taskReferencesProblems.length).toBeGreaterThan(
      0,
    );
    expect(
      afterRender.context.taskReferencesProblems[0].errors[0].message,
    ).toMatch(/non existing variable/i);

    service.stop();
  });

  it("keeps a 400 that omits validationErrors after the flow re-renders", async () => {
    const service = startInspector();

    await waitForState(service, (state) => state.matches("errorsDisplay"));

    service.send({
      type: ErrorInspectorEventTypes.REPORT_SERVER_ERROR,
      text: SERVER_MESSAGE,
    });
    service.send({
      type: ErrorInspectorEventTypes.FLOW_FINISHED_RENDERING,
      nodes: [],
    });

    const afterRender = await waitForState(service, (state) =>
      state.matches("errorsDisplay.missingReferences.referencesMenus"),
    );

    expect(afterRender.context.serverErrors).toHaveLength(1);
    expect(afterRender.context.serverErrors[0].message).toBe(SERVER_MESSAGE);

    service.stop();
  });
});
