import { createMachine } from "xstate";
import {
  ErrorInspectorMachineContext,
  ErrorInspectorEventTypes,
  ErrorInspectorMachineEvents,
} from "./types";
import * as actions from "./actions";
import {
  testForRemovedTaskReferencesService,
  fetchSecretsEndEnvironmentsList,
} from "./service";

export const errorInspectorMachine = createMachine<
  ErrorInspectorMachineContext,
  ErrorInspectorMachineEvents
>(
  {
    id: "errorInspectorMachine",
    predictableActionArguments: true,
    initial: "fetchForSecrets",
    context: {
      currentWf: undefined,
      workflowErrors: [],
      taskErrors: [],
      serverErrors: [],
      runWorkflowErrors: [],
      crumbMap: undefined,
      workflowReferenceProblems: [],
      taskReferencesProblems: [],
      unreachableTaskProblems: [],
      authHeaders: {},
      expanded: false,
    },
    on: {
      [ErrorInspectorEventTypes.SET_WORKFLOW]: {
        actions: ["persistCurrentWorkflow"],
      },
      [ErrorInspectorEventTypes.TOGGLE_ERROR_INSPECTOR]: {
        actions: ["toggleErrorInspector"],
      },
      [ErrorInspectorEventTypes.SET_ERROR_INSPECTOR_EXPANDED]: {
        actions: ["setErrorInspectorExpanded", "cleanImportSummary"],
      },
      [ErrorInspectorEventTypes.SET_ERROR_INSPECTOR_COLLAPSED]: {
        actions: ["setErrorInspectorCollapsed", "cleanImportSummary"],
      },
      [ErrorInspectorEventTypes.COLLAPSE_INSPECTOR_IF_NO_ERRORS]: {
        actions: ["collapseInspectorIfNoErrors"],
      },
      [ErrorInspectorEventTypes.REPORT_SERVER_ERROR]: {
        actions: ["persistServerError"],
      },
    },
    states: {
      fetchForSecrets: {
        invoke: {
          src: "fetchSecretsEndEnvironmentsList",
          id: "fetch-secrets-and-environments",
          onDone: {
            actions: ["updateSecretEnvs"],
            target: "errorsDisplay",
          },
          onError: {
            target: "errorsDisplay",
          },
        },
      },
      errorsDisplay: {
        type: "parallel",
        states: {
          controlledErrors: {
            on: {
              [ErrorInspectorEventTypes.REPORT_FLOW_ERROR]: {
                actions: ["flowErrorToWorkflowError"],
                target: ".testState",
              },
              [ErrorInspectorEventTypes.VALIDATE_WORKFLOW_STRING]: {
                actions: ["testForErrorsInStringWorkflow"],
                target: ".testState",
              },
              [ErrorInspectorEventTypes.VALIDATE_WORKFLOW]: {
                actions: ["testForErrors"],
                target: ".testState",
              },
              [ErrorInspectorEventTypes.CLEAN_SERIALIZATION_ERROR]: {
                actions: [
                  "cleanSerializationError",
                  "raiseCollapseErrorInspectorIfNoErrors",
                ],
              },
            },
            initial: "idle",
            states: {
              idle: {
                entry: "notifyErrorFree",
              },
              withErrors: {
                type: "parallel",
                entry: "workflowHasErrors",
                states: {
                  taskErrorsViewer: {
                    initial: "collapsed",
                    states: {
                      collapsed: {
                        on: {
                          [ErrorInspectorEventTypes.TOGGLE_TASK_ERRORS_VIEWER]:
                            {
                              target: "expanded",
                            },
                        },
                      },
                      expanded: {
                        on: {
                          [ErrorInspectorEventTypes.TOGGLE_TASK_ERRORS_VIEWER]:
                            {
                              target: "collapsed",
                            },
                          [ErrorInspectorEventTypes.CLICK_REFERENCE]: {
                            actions: ["setErrorInspectorCollapsed"],
                            target: "collapsed",
                          },
                        },
                      },
                    },
                  },
                  workflowErrorsViewer: {
                    initial: "collapsed",
                    states: {
                      collapsed: {
                        on: {
                          [ErrorInspectorEventTypes.TOGGLE_WORKFLOW_ERRORS_VIEWER]:
                            {
                              target: "expanded",
                            },
                        },
                      },
                      expanded: {
                        on: {
                          [ErrorInspectorEventTypes.TOGGLE_WORKFLOW_ERRORS_VIEWER]:
                            {
                              target: "collapsed",
                            },
                          [ErrorInspectorEventTypes.CLICK_REFERENCE]: {
                            actions: ["setErrorInspectorCollapsed"],
                            target: "collapsed",
                          },
                          [ErrorInspectorEventTypes.JUMP_TO_FIRST_ERROR]: {
                            actions: [
                              "sendJumpToFirstError",
                              "setErrorInspectorCollapsed",
                            ],
                          },
                        },
                      },
                    },
                  },
                },
              },
              testState: {
                always: [
                  {
                    target: "idle",
                    cond: (context) => {
                      const { workflowErrors, taskErrors } = context;
                      return (
                        workflowErrors.length === 0 && taskErrors.length === 0
                      );
                    },
                  },
                  { target: "withErrors" },
                ],
              },
            },
          },
          serverErrors: {
            on: {
              [ErrorInspectorEventTypes.REPORT_SERVER_ERROR]: {
                actions: ["persistServerError", "raiseExpandErrorInspector"],
              },
              [ErrorInspectorEventTypes.REPORT_RUN_ERROR]: {
                actions: ["persistRunError", "raiseExpandErrorInspector"],
              },
              [ErrorInspectorEventTypes.CLEAN_RUN_ERRORS]: {
                actions: ["cleanRunError", "raiseCollapseErrorInspector"],
              },
              [ErrorInspectorEventTypes.CLEAN_SERVER_ERRORS]: {
                actions: [
                  "cleanServerErrors",
                  "raiseCollapseErrorInspectorIfNoErrors",
                ],
              },
              [ErrorInspectorEventTypes.CLICK_REFERENCE]: {
                actions: ["sendCancelConfirmSave", "sendReferenceText"],
              },
              [ErrorInspectorEventTypes.VALIDATE_WORKFLOW]: {
                actions: [
                  "verifyChangesInServerErrors",
                  "collapseInspectorIfNoErrors",
                ],
              },
              [ErrorInspectorEventTypes.FLOW_FINISHED_RENDERING]: {
                actions: ["removeServerErrorsRelatedToRemovedTasks"],
              },
            },
          },
          missingReferences: {
            // missingReferences wont prevent the ui from rendering
            on: {
              [ErrorInspectorEventTypes.FLOW_FINISHED_RENDERING]: {
                actions: ["persistCrumbMap"],
                target: ".testForMissingReferences",
              },
            },
            initial: "referencesMenus",
            states: {
              testForMissingReferences: {
                invoke: {
                  src: "testForRemovedTaskReferencesService",
                  id: "testRemovedTaskReferences",
                  onDone: {
                    actions: ["persistReferenceProblems"],
                    target: "referencesMenus",
                  },
                },
              },
              referencesMenus: {
                type: "parallel",
                states: {
                  taskReferences: {
                    initial: "collapsed",
                    states: {
                      collapsed: {
                        on: {
                          [ErrorInspectorEventTypes.TOGGLE_TASK_REFERENCE_ERRORS_VIEWER]:
                            {
                              target: "expanded",
                            },
                        },
                      },
                      expanded: {
                        on: {
                          [ErrorInspectorEventTypes.TOGGLE_TASK_REFERENCE_ERRORS_VIEWER]:
                            {
                              target: "collapsed",
                            },
                          [ErrorInspectorEventTypes.CLICK_REFERENCE]: {
                            actions: [
                              "sendReferenceText",
                              "setErrorInspectorCollapsed",
                            ],
                          },
                        },
                      },
                    },
                  },
                  workflowReferences: {
                    initial: "collapsed",
                    states: {
                      collapsed: {
                        on: {
                          [ErrorInspectorEventTypes.TOGGLE_WORKFLOW_REFERENCE_ERRORS_VIEWER]:
                            {
                              target: "expanded",
                            },
                        },
                      },
                      expanded: {
                        on: {
                          [ErrorInspectorEventTypes.TOGGLE_WORKFLOW_REFERENCE_ERRORS_VIEWER]:
                            {
                              target: "collapsed",
                            },
                          [ErrorInspectorEventTypes.CLICK_REFERENCE]: {
                            actions: ["sendReferenceText"],
                          },
                        },
                      },
                    },
                  },
                },
              },
            },
          },
        },
      },
    },
  },
  {
    actions: actions as any,
    services: {
      testForRemovedTaskReferencesService,
      fetchSecretsEndEnvironmentsList,
    },
  },
);
