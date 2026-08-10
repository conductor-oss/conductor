import { createMachine, DoneInvokeEvent, forwardTo } from "xstate";
import {
  TaskDefinitionMachineContext,
  TaskDefinitionMachineEvent,
  TaskDefinitionMachineEventType,
  TaskDefinitionMachineState,
} from "./types";
import { TaskDefinitionDto } from "types/TaskDefinition";
import * as customActions from "./actions";
import * as guards from "./guards";
import * as services from "./services";
import { taskDefinitionFormMachine } from "pages/definition/task/form/state";
import { TASK_FORM_MACHINE_ID } from "pages/definition/task/state/helpers";

export const taskDefinitionMachine = createMachine<
  TaskDefinitionMachineContext,
  TaskDefinitionMachineEvent
>(
  {
    id: "taskDefinitionMachine",
    predictableActionArguments: true,
    initial: TaskDefinitionMachineState.INIT,
    context: {
      authHeaders: {},
      isContinueCreate: false,
      isNewTaskDef: true,
      originTaskDefinitionString: "",
      modifiedTaskDefinitionString: "",
      modifiedTaskDefinition: {} as TaskDefinitionDto,
      originTaskDefinition: {} as TaskDefinitionDto,
      originTaskDefinitions: [] as TaskDefinitionDto[],
      testInputParameters: "{}",
      testTaskDomain: "",
      couldNotParseJson: false,
      lastSelectedTab: undefined,
    },
    on: {
      [TaskDefinitionMachineEventType.SET_TASK_DEFINITION]: {
        actions: ["setNameOnOriginTaskDefinition"],
        target: TaskDefinitionMachineState.INIT,
      },
    },
    states: {
      [TaskDefinitionMachineState.INIT]: {
        always: [
          {
            cond: "isEditTaskDefinition",
            target: TaskDefinitionMachineState.FETCH_FOR_TASK_DEFINITION,
          },
          { target: TaskDefinitionMachineState.READY },
        ],
      },
      [TaskDefinitionMachineState.FETCH_FOR_TASK_DEFINITION]: {
        invoke: {
          src: "fetchTaskDefinitionByNameService",
          onDone: {
            target: TaskDefinitionMachineState.READY,
            actions: ["persistTaskDefinitionByName"],
          },
          onError: {
            target: TaskDefinitionMachineState.FINISH,
            actions: ["setErrorMessage"],
          },
        },
      },
      [TaskDefinitionMachineState.READY]: {
        type: "parallel",
        states: {
          [TaskDefinitionMachineState.MAIN_CONTAINER]: {
            initial: TaskDefinitionMachineState.FORM,
            states: {
              [TaskDefinitionMachineState.FORM]: {
                initial: "idle",
                states: {
                  idle: {
                    on: {
                      [TaskDefinitionMachineEventType.TOGGLE_FORM_MODE]: {
                        actions: forwardTo(TASK_FORM_MACHINE_ID),
                      },
                      [TaskDefinitionMachineEventType.CONFIRM_RESET_TASK]: {
                        actions: forwardTo(TASK_FORM_MACHINE_ID),
                      },
                      [TaskDefinitionMachineEventType.SET_SAVE_CONFIRMATION_OPEN]:
                        {
                          actions: [
                            forwardTo(TASK_FORM_MACHINE_ID),
                            "changeIsContinueCreate",
                          ],
                        },
                      // The form handles this event. since it has the last version
                      [TaskDefinitionMachineEventType.EXPORT_TASK_TO_JSON_FILE]:
                        {
                          actions: forwardTo(TASK_FORM_MACHINE_ID),
                        },
                    },
                    invoke: {
                      src: taskDefinitionFormMachine,
                      id: TASK_FORM_MACHINE_ID,
                      data: ({
                        modifiedTaskDefinition,
                        originTaskDefinition,
                        isNewTaskDef,
                      }) => ({
                        modifiedTaskDefinition,
                        originTaskDefinition,
                        isNewTaskDef,
                      }),
                      onDone: [
                        {
                          target: `#taskDefinitionMachine.${TaskDefinitionMachineState.READY}.${TaskDefinitionMachineState.MAIN_CONTAINER}.${TaskDefinitionMachineState.EDITOR}`,
                          cond: (
                            __context: TaskDefinitionMachineContext,
                            event: DoneInvokeEvent<{
                              reason: TaskDefinitionMachineEventType;
                            }>,
                          ) => {
                            return (
                              event.data.reason ===
                              TaskDefinitionMachineEventType.TOGGLE_FORM_MODE
                            );
                          },
                          actions: ["syncDataFromFormMachine"],
                        } as any,
                        {
                          target: `#taskDefinitionMachine.${TaskDefinitionMachineState.READY}.${TaskDefinitionMachineState.MAIN_CONTAINER}.${TaskDefinitionMachineState.DIFF_EDITOR}`,
                          cond: (
                            __context: TaskDefinitionMachineContext,
                            event: DoneInvokeEvent<{
                              reason: TaskDefinitionMachineEventType;
                            }>,
                          ) => {
                            return (
                              event.data.reason ===
                              TaskDefinitionMachineEventType.SET_SAVE_CONFIRMATION_OPEN
                            );
                          },
                          actions: ["syncDataFromFormMachine"],
                        },
                        { target: "idle" },
                      ],
                    },
                  },
                },
              },
              [TaskDefinitionMachineState.EDITOR]: {
                entry: "cleanLastSelectedTab", // Note if last selected tab is undefined it will go to the form
                on: {
                  [TaskDefinitionMachineEventType.HANDLE_CHANGE_TASK_DEFINITION]:
                    [
                      {
                        actions: ["handleChangeTaskDefinition"],
                      },
                    ],
                  [TaskDefinitionMachineEventType.DEBOUNCE_HANDLE_CHANGE_TASK_DEFINITION]:
                    {
                      actions: [
                        "cancelDebounceChangeTaskDefinition",
                        "debounceChangeTaskDefinition",
                      ],
                    },
                  [TaskDefinitionMachineEventType.TOGGLE_FORM_MODE]: {
                    target: TaskDefinitionMachineState.FORM,
                  },
                  [TaskDefinitionMachineEventType.SET_SAVE_CONFIRMATION_OPEN]: {
                    actions: ["changeIsContinueCreate"],
                    target: TaskDefinitionMachineState.DIFF_EDITOR,
                  },
                },
                initial: "idle",
                states: {
                  idle: {
                    on: {
                      [TaskDefinitionMachineEventType.EXPORT_TASK_TO_JSON_FILE]:
                        {
                          target: "exportTask",
                        },
                    },
                  },
                  exportTask: {
                    invoke: {
                      src: "handleDownloadFile",
                      onDone: {
                        target: "idle",
                      },
                      onError: {
                        actions: ["setErrorMessage"],
                      },
                    },
                  },
                },
              },
              [TaskDefinitionMachineState.DIFF_EDITOR]: {
                initial: "idle",
                states: {
                  idle: {
                    on: {
                      [TaskDefinitionMachineEventType.HANDLE_CHANGE_TASK_DEFINITION]:
                        [
                          {
                            actions: ["handleChangeTaskDefinition"],
                          },
                        ],
                      [TaskDefinitionMachineEventType.DEBOUNCE_HANDLE_CHANGE_TASK_DEFINITION]:
                        {
                          actions: [
                            "cancelDebounceChangeTaskDefinition",
                            "debounceChangeTaskDefinition",
                          ],
                        },
                      [TaskDefinitionMachineEventType.CANCEL_CONFIRM_SAVE]: {
                        target: `#taskDefinitionMachine.${TaskDefinitionMachineState.READY}.${TaskDefinitionMachineState.MAIN_CONTAINER}.${TaskDefinitionMachineState.EDITOR}`, // not really editor though should return to previous tab
                      },
                      [TaskDefinitionMachineEventType.SAVE_TASK_DEFINITION]: {
                        target: "createTaskDefinition",
                      },
                    },
                  },
                  createTaskDefinition: {
                    invoke: {
                      src: "createOrUpdateTaskDefinitionService",
                      onDone: [
                        {
                          cond: "isContinueCreate",
                          target: `#taskDefinitionMachine.${TaskDefinitionMachineState.INIT}`,

                          actions: [
                            "showSaveSuccessMessage",
                            "prepareNewTaskContext",
                            "redirectToNewTask",
                          ],
                        },
                        {
                          target: `#taskDefinitionMachine.${TaskDefinitionMachineState.INIT}`,
                          actions: [
                            "updateOriginTaskDefinition",
                            "showSaveSuccessMessage",
                            "setIsEditTaskDef",
                            "redirectToEditTask",
                          ],
                        },
                      ],
                      onError: [
                        {
                          cond: "lastTabWasForm",
                          target: `#taskDefinitionMachine.${TaskDefinitionMachineState.READY}.${TaskDefinitionMachineState.MAIN_CONTAINER}.${TaskDefinitionMachineState.FORM}`,
                          actions: ["setErrorMessage", "cleanLastSelectedTab"],
                        },
                        {
                          target: `#taskDefinitionMachine.${TaskDefinitionMachineState.READY}.${TaskDefinitionMachineState.MAIN_CONTAINER}.${TaskDefinitionMachineState.EDITOR}`,
                          actions: ["setErrorMessage", "cleanLastSelectedTab"],
                        },
                      ],
                    },
                  },
                },
              },
              // Move to parallel state
            },
          },
          [TaskDefinitionMachineState.TASK_TESTER]: {
            initial: "idle",
            states: {
              idle: {
                on: {
                  [TaskDefinitionMachineEventType.SET_INPUT_PARAMETERS]: {
                    actions: ["setInputParameters"],
                  },
                  [TaskDefinitionMachineEventType.SET_TASK_DOMAIN]: {
                    actions: ["setTaskDomain"],
                  },
                  [TaskDefinitionMachineEventType.HANDLE_RUN_TEST_TASK]: {
                    target: "runTestTask",
                  },
                },
              },
              runTestTask: {
                invoke: {
                  src: "runTestTaskService",
                  onDone: {
                    actions: ["persistWorkflowId"],
                    target: "idle",
                  },
                  onError: {
                    actions: ["setErrorMessage"],
                    target: "idle",
                  },
                },
              },
            },
          },
          [TaskDefinitionMachineState.RESET_FORM]: {
            initial: "idle",
            states: {
              idle: {
                on: {
                  [TaskDefinitionMachineEventType.SET_RESET_CONFIRMATION_OPEN]:
                    {
                      target: TaskDefinitionMachineState.RESET_FORM_CONFIRM,
                    },
                },
              },
              [TaskDefinitionMachineState.RESET_FORM_CONFIRM]: {
                on: {
                  [TaskDefinitionMachineEventType.CONFIRM_RESET_TASK]: {
                    actions: ["resetContext"],
                    target: "idle",
                  },
                  [TaskDefinitionMachineEventType.CANCEL_CONFIRM_SAVE]: {
                    target: "idle",
                  },
                },
              },
            },
          },
          [TaskDefinitionMachineState.DELETE_FORM]: {
            initial: "idle",
            states: {
              idle: {
                on: {
                  [TaskDefinitionMachineEventType.SET_DELETE_CONFIRMATION_OPEN]:
                    {
                      target: TaskDefinitionMachineState.DELETE_FORM_CONFIRM,
                    },
                },
              },
              [TaskDefinitionMachineState.DELETE_FORM_CONFIRM]: {
                on: {
                  [TaskDefinitionMachineEventType.CONFIRM_RESET_TASK]: {
                    target: "deleteTaskDefinition",
                  },
                  [TaskDefinitionMachineEventType.CANCEL_CONFIRM_SAVE]: {
                    target: "idle",
                  },
                },
              },
              deleteTaskDefinition: {
                invoke: {
                  src: "deleteTaskDefinitionService",
                  onDone: {
                    actions: ["redirectToTaskList"],
                  },
                  onError: {
                    target: `#taskDefinitionMachine.${TaskDefinitionMachineState.READY}`,
                    actions: ["setErrorMessage"],
                  },
                },
              },
            },
          },
        },
      },
      [TaskDefinitionMachineState.FINISH]: {
        type: "final",
      },
    },
  },
  {
    actions: customActions as any,
    guards: guards as any,
    services: services as any,
  },
);
