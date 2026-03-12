import { createMachine, assign } from "xstate";
import * as customActions from "./actions";
import * as services from "./service";
import {
  TestTaskButtonTypes,
  TestTaskButtonMachineContext,
  TestTaskButtonEvents,
  TestTaskButtonMachineStates,
} from "./types";

export const TestTaskMachine = createMachine<
  TestTaskButtonMachineContext,
  TestTaskButtonEvents
>(
  {
    id: "testTaskMachine",
    predictableActionArguments: true,
    initial: "configuringTask",
    context: {
      originalTask: {},
      taskChanges: {},
      testedTaskExecutionResult: {},
      authHeaders: {},
      tasksList: [],
    },
    states: {
      configuringTask: {
        on: {
          [TestTaskButtonTypes.SET_TASK_JSON]: {
            actions: assign({
              originalTask: (_, event) => event.originalTask,
              taskChanges: (_, event) => event.taskChanges,
            }),
          },
          [TestTaskButtonTypes.UPDATE_TASK_VARIABLES]: {
            actions: ["persistTaskChanges"],
          },
          [TestTaskButtonTypes.SET_TASK_DOMAIN]: {
            actions: ["setTaskDomain"],
          },
          [TestTaskButtonTypes.TEST_TASK]: {
            target: TestTaskButtonMachineStates.RUN_TEST_TASK,
          },
        },
      },
      [TestTaskButtonMachineStates.RUN_TEST_TASK]: {
        initial: "runTestTask",
        states: {
          runTestTask: {
            invoke: {
              id: "runTestTask",
              src: "runTestTask",
              onDone: {
                target: "pollForExecutionResult",
                actions: ["persistExecutionId"],
              },
              onError: {
                target: "#testTaskMachine.configuringTask",
                actions: ["setErrorMessage"],
              },
            },
          },
          pollForExecutionResult: {
            invoke: {
              id: "pollForExecutionResult",
              src: "pollForExecutionResult",
              onDone: [
                {
                  cond: (_context, { data: { status } }) =>
                    status === "RUNNING",
                  target: "keepPolling",
                  actions: ["persistTestedTaskExecutionResult"],
                },
                {
                  target: "displayOutput",
                  actions: ["persistTestedTaskExecutionResult"],
                },
              ],
              onError: {
                target: "#testTaskMachine.configuringTask",
                actions: ["setErrorMessage"],
              },
            },
          },
          keepPolling: {
            after: {
              1000: {
                target: "pollForExecutionResult",
              },
            },
          },
          displayOutput: {
            type: "final",
          },
        },
      },
    },
  },
  {
    actions: customActions as any,
    services: services as any,
  },
);
