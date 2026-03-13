import { crumbsToTaskSteps } from "components/flow/nodes";
import { FlowActionTypes } from "components/flow/state";
import { flowMachine } from "components/flow/state/machine";
import _flow from "lodash/flow";
import _isEmpty from "lodash/isEmpty";
import _last from "lodash/last";
import _prop from "lodash/property";
import { assign, createMachine, spawn } from "xstate";
import {
  localCopyMachine,
  LocalCopyMachineEventTypes,
  removeCopyFromStorage,
} from "../ConfirmLocalCopyDialog/state";
import {
  codeMachine,
  CodeMachineEventTypes,
} from "../EditorPanel/CodeEditorTab/state";
import { formMachine } from "../EditorPanel/TaskFormTab/state";
import { IdempotencyStrategyEnum, runMachine } from "../RunWorkflow/state";
import { workflowMetadataMachine } from "../WorkflowMetadata/state";
import {
  saveMachine,
  SaveWorkflowMachineEventTypes,
} from "../confirmSave/state";
import {
  ErrorInspectorEventTypes,
  errorInspectorMachine,
} from "../errorInspector/state";
import { extractWorkflowMetadata } from "../helpers";
import * as actions from "./action";
import { WORKFLOW_TAB } from "./constants";
import * as guards from "./guards";
import {
  fetchForImportedTemplateImportSummary,
  fetchInputSchema,
  fetchSecretsEndEnvironmentsList,
  persistCopyInLocalStorage,
  refetchCurrentWorkflowVersionsService,
} from "./services";
import {
  DefinitionMachineContext,
  DefinitionMachineEventTypes,
  WorkflowDefinitionEvents,
} from "./types";

const selectedTaskReferenceName = _flow([_last, _prop("ref")]);

export const workflowDefinitionMachine = createMachine<
  DefinitionMachineContext,
  WorkflowDefinitionEvents
>(
  {
    predictableActionArguments: true,
    id: "workflowDefinitionMachine",
    initial: "init",
    context: {
      successfullyImportedWorkflowId: undefined,
      currentWf: undefined,
      workflowChanges: {},
      isNewWorkflow: false,
      workflowName: undefined,
      currentVersion: undefined,
      workflowVersions: [],
      selectedTaskCrumbs: [],
      authHeaders: {}, // This should be in auth actor
      message: {
        text: undefined,
        severity: undefined,
      },
      localCopyMessage: undefined,
      openedTab: WORKFLOW_TAB,
      previousTab: WORKFLOW_TAB,
      lastPerformedOperation: undefined,
      errorInspectorMachine: undefined,
      downloadFileObj: undefined,
      lastRemovalOperation: undefined,
      workflowTemplateId: undefined,
      collapseWorkflowList: [],
      isAgentExpanded: false,
    },
    on: {
      [DefinitionMachineEventTypes.UPDATE_ATTRIBS_EVT]: {
        actions: ["persistWorkflowAttribs"],
        target: "fetchForVersions",
      },
      [DefinitionMachineEventTypes.TOGGLE_META_BAR_EDIT_MODE_EVT]: {
        actions: ["toggleMetaBarEditMode"],
      },
      [DefinitionMachineEventTypes.TOGGLE_AGENT_EXPANDED]: {
        actions: ["toggleAgentExpanded"],
      },
    },
    states: {
      fetchForVersions: {
        invoke: {
          src: "refetchCurrentWorkflowVersionsService",
          onDone: {
            actions: ["persistWorkflowVersionsParsed"],
            target: "checkLocalStorage",
          },
        },
      },
      init: {
        entry: assign({
          errorInspectorMachine: (ctx: DefinitionMachineContext) =>
            spawn(
              errorInspectorMachine.withContext({
                authHeaders: ctx.authHeaders,
                currentWf: undefined,
                workflowErrors: [],
                taskErrors: [],
                serverErrors: [],
                crumbMap: undefined,
                workflowReferenceProblems: [],
                taskReferencesProblems: [],
                unreachableTaskProblems: [],
                runWorkflowErrors: [],
              }),
              "errorInspectorMachine",
            ),
          // @ts-ignore
          flowMachine: (ctx: DefinitionMachineContext) =>
            spawn(
              flowMachine.withContext({
                authHeaders: ctx.authHeaders,
                currentWf: {},
                selectedNodeIdx: undefined,
                nodes: [],
                edges: [],
                menuOperationContext: undefined,
                openedNode: undefined,
              }),
              "flowMachine",
            ),
        }),
      },
      checkLocalStorage: {
        invoke: {
          id: "localCopyMachine",
          src: localCopyMachine,
          data: {
            lastStoredVersion: ({
              workflowChanges,
            }: DefinitionMachineContext) => workflowChanges,
            workflowName: ({ workflowName }: DefinitionMachineContext) =>
              workflowName,
            currentVersion: ({ currentVersion }: DefinitionMachineContext) =>
              currentVersion,
            isNewWorkflow: ({ isNewWorkflow }: DefinitionMachineContext) =>
              isNewWorkflow,
            currentWf: ({ currentWf }: DefinitionMachineContext) => currentWf,
          },
          onDone: {
            actions: [
              "updateWfFromLocalStorage",
              "maybePersistLocalCopyMessage",
            ],
            target: ["presentEditor"],
          },
          onError: {},
        },
        entry: "forwardWorkflowToLocalCopyMachine",
      },
      presentEditor: {
        always: [
          // condition has changes from localStorage send to ready\
          {
            cond: "isNewWorkflow",
            target: "ready",
          },

          {
            target: "fetchForWorkflow",
            cond: ({
              currentVersion,
              workflowVersions,
            }: DefinitionMachineContext) =>
              currentVersion !== null || workflowVersions?.length > 1,
          },
          {
            target: "backToList",
            cond: ({ currentVersion }: DefinitionMachineContext) =>
              currentVersion === null,
          },
        ],
      },
      backToList: {
        initial: "waitForActions",
        states: {
          waitForActions: {
            after: {
              100: "goBack",
            },
          },
          goBack: {
            entry: "goBackToDefinitionSelection",
            type: "final",
          },
        },
      },
      fetchForWorkflow: {
        invoke: {
          src: "fetchWorkflow",
          onDone: {
            actions: ["updateWf"],
            target: "fetchForInputSchema",
          },
          onError: {
            actions: ["processErrorFetching"],
            target: "errorFetchingWorkflow",
          },
        },
      },
      fetchForInputSchema: {
        invoke: {
          src: "fetchInputSchema",
          onDone: {
            actions: ["updateWfDefaultRunParam"],
            target: "ready",
          },
          onError: {
            target: "ready",
          },
        },
      },
      ready: {
        entry: "sendWorkflowToInspector",
        type: "parallel",
        states: {
          agentAutoExpand: {
            initial: "waiting",
            states: {
              waiting: {
                after: {
                  // Delay expansion to allow workflow diagram to render first
                  400: {
                    target: "expanded",
                    actions: "autoExpandAgentForNewWorkflow",
                    cond: "isNewWorkflow",
                  },
                },
              },
              expanded: {
                type: "final",
              },
            },
          },
          rightPanel: {
            initial: "opened",
            on: {
              [DefinitionMachineEventTypes.PERFORM_OPERATION_EVT]: {
                target:
                  "#workflowDefinitionMachine.ready.rightPanel.opened.pendingSelections",
                cond: "isAddOperation",
              },
              [FlowActionTypes.SELECT_NODE_EVT]: {
                target:
                  "#workflowDefinitionMachine.ready.rightPanel.opened.tabFocus",
                cond: "isValidSelection",
              },
              [DefinitionMachineEventTypes.SYNC_RUN_CONTEXT_AND_CHANGE_TAB]: {
                actions: [
                  "updateRunTabFormState",
                  "changeTab",
                  "gtagEventLogger",
                  "cleanRunErrors",
                ],
                target:
                  "#workflowDefinitionMachine.ready.rightPanel.opened.tabFocus",
              },
              [DefinitionMachineEventTypes.COLLAPSE_SIDEBAR_AND_RIGHT_PANEL]: {
                actions: ["closeLeftSidebar"],
                target: "#workflowDefinitionMachine.ready.rightPanel.closed",
              },
            },
            states: {
              opened: {
                initial: "pendingSelections",
                states: {
                  refetchCurrentWorkflowVersions: {
                    invoke: {
                      src: "refetchCurrentWorkflowVersionsService",
                      id: "refetch-wf-versions",
                      onError: {
                        target: "#workflowDefinitionMachine.backToList",
                        actions: ["logStuff"],
                      },
                      onDone: [
                        {
                          cond: "isLastVersion",
                          actions: ["resetChanges"], //reset changes so we dont get trapped in the dialog
                          target: "#workflowDefinitionMachine.backToList",
                        },
                        {
                          actions: ["persistLatestVersion", "pushToHistory"],
                        },
                      ],
                    },
                  },
                  deleteWorkflow: {
                    invoke: {
                      src: "deleteWorkflowVersion",
                      id: "delete-workflow",
                      onError: {
                        actions: ["logStuff"],
                      },
                      onDone: {
                        actions: ["removeLocalCopy", "resetCurrentVersion"],
                        target: "refetchCurrentWorkflowVersions",
                      },
                    },
                  },

                  pendingSelections: {
                    always: [
                      {
                        target: "addressPendingSelections",
                        cond: "hasLastPerformedOperation",
                      },
                      { target: "tabFocus" },
                    ],
                  },
                  addressPendingSelections: {
                    on: {
                      [DefinitionMachineEventTypes.FLOW_FINISHED_RENDERING]: {
                        actions: ["selectNewTask", "cleanLastOperation"],
                        target: "tabFocus",
                      },
                      [DefinitionMachineEventTypes.CHANGE_TAB_EVT]: {
                        // In case something fails allow user to change tabs
                        actions: [
                          "changeTab",
                          "cleanLastOperation",
                          "gtagEventLogger",
                        ],
                        target: "tabFocus",
                      },
                    },
                  },
                  codeEditor: {
                    exit: ["cleanCodeTextReference", "cleanSerializationError"],
                    invoke: {
                      src: codeMachine,
                      id: "codeMachine",
                      data: {
                        originalWorkflow: ({
                          currentWf,
                        }: DefinitionMachineContext) => currentWf,
                        editorChanges: ({
                          workflowChanges,
                        }: DefinitionMachineContext) =>
                          JSON.stringify(workflowChanges, null, 2),
                        errorInspectorMachine: ({
                          errorInspectorMachine,
                        }: DefinitionMachineContext) => errorInspectorMachine,
                        referenceText: ({
                          selectedTaskCrumbs,
                          codeTextReference,
                        }: DefinitionMachineContext) => {
                          // Has a persisted error
                          if (codeTextReference) {
                            return codeTextReference;
                          }

                          // maybe has a selected task
                          const selectedTaskReference =
                            selectedTaskReferenceName(selectedTaskCrumbs || []);
                          if (selectedTaskReference) {
                            return {
                              textReference: selectedTaskReference,
                              referenceReason: "info",
                            };
                          }
                          return undefined;
                        },
                      },
                    },
                    on: {
                      [ErrorInspectorEventTypes.WORKFLOW_WITH_NO_ERRORS]: {
                        actions: [
                          "persistWorkflowChanges",
                          "notifyFlowUpdates",
                          "startRenderingGtag",
                        ],
                        cond: "workflowWasSentWithNoErrors",
                      },
                      [ErrorInspectorEventTypes.WORKFLOW_HAS_ERRORS]: {
                        actions: ["persistWorkflowChanges"],
                        cond: "workflowWasSentWithNoErrors",
                      },
                      [DefinitionMachineEventTypes.CHANGE_TAB_EVT]: [
                        {
                          cond: "comesFromCodeAimsTaskTabHasSelectedTask",
                          actions: ["reSelectTaskIfSelected"],
                          target: "tabFocus",
                        },
                        {
                          actions: ["changeTab"],
                          cond: "isDifferentTab",
                          target: "tabFocus",
                        },
                      ],
                      [CodeMachineEventTypes.HIGHLIGHT_TEXT_REFERENCE]: {
                        actions: "forwardToCodeMachine",
                      },
                      [CodeMachineEventTypes.JUMP_TO_FIRST_ERROR]: {
                        actions: "forwardToCodeMachine",
                      },
                    },
                  },
                  taskEditor: {
                    invoke: {
                      id: "formTaskMachine",
                      src: formMachine,
                      data: ({
                        selectedTaskCrumbs,
                        workflowChanges,
                        authHeaders,
                      }: DefinitionMachineContext) => {
                        const tasksBranch = _isEmpty(selectedTaskCrumbs)
                          ? []
                          : crumbsToTaskSteps(
                              selectedTaskCrumbs,
                              workflowChanges?.tasks || [],
                            );
                        const crumbs = selectedTaskCrumbs;
                        const workflowInputParameters =
                          workflowChanges?.inputParameters;
                        const originalTask = _last(tasksBranch);
                        return {
                          originalTask,
                          taskChanges: originalTask,
                          crumbs,
                          tasksBranch,
                          workflowInputParameters,
                          authHeaders,
                        };
                      },
                    },
                    on: {
                      [ErrorInspectorEventTypes.WORKFLOW_WITH_NO_ERRORS]: {
                        actions: ["forwardCleanWorkflow", "sendCrumbUpdates"],
                        cond: "workflowWasSentWithNoErrors",
                      },
                      [ErrorInspectorEventTypes.WORKFLOW_HAS_ERRORS]: {
                        actions: ["setFlowAsReadOnly"],
                        cond: "workflowWasSentWithNoErrors",
                      },
                      [DefinitionMachineEventTypes.REPLACE_TASK_EVT]: {
                        actions: [
                          "replaceTask",
                          "validateWorkflow",
                          "updateSelectedCrumbs",
                          "gtagEventLogger",
                        ],
                      },
                      [DefinitionMachineEventTypes.CHANGE_TAB_EVT]: {
                        actions: ["changeTab", "gtagEventLogger"],
                        cond: "isDifferentTab",
                        target: "tabFocus",
                      },
                      [FlowActionTypes.SELECT_NODE_EVT]: {
                        target: "tabFocusAfter",
                        cond: "isValidSelection",
                      },
                      [FlowActionTypes.SELECT_EDGE_EVT]: {
                        actions: ["forwardSelectEdge"],
                      },
                      [FlowActionTypes.UPDATE_COLLAPSE_WORKFLOW_LIST]: {
                        actions: ["forwardCollapseWorkflowList"],
                      },
                      [DefinitionMachineEventTypes.FLOW_FINISHED_RENDERING]: {
                        actions: ["updateCollapseWorkflowList"],
                      },
                    },
                  },
                  runWorkflow: {
                    tags: ["runWorkflowTab"],
                    invoke: {
                      id: "runWorkflowMachine",
                      src: runMachine,
                      data: {
                        authHeaders: ({
                          authHeaders,
                        }: DefinitionMachineContext) => authHeaders,
                        currentWf: ({
                          currentWf,
                          workflowName,
                        }: DefinitionMachineContext) => ({
                          ...currentWf,
                          name: workflowName, // This allows running shared workflows. we only update workflowName when workflow is saved
                        }),
                        errorInspectorMachine: ({
                          errorInspectorMachine,
                        }: DefinitionMachineContext) => errorInspectorMachine,
                        input: ({
                          runTabFormState,
                        }: DefinitionMachineContext) =>
                          runTabFormState?.input ?? "{}",
                        workflowDefaultRunParam: ({
                          workflowDefaultRunParam,
                        }: DefinitionMachineContext) => workflowDefaultRunParam,
                        correlationId: ({
                          runTabFormState,
                        }: DefinitionMachineContext) =>
                          runTabFormState?.correlationId ?? undefined,
                        taskToDomain: ({
                          runTabFormState,
                        }: DefinitionMachineContext) =>
                          runTabFormState?.taskToDomain ?? "{}",
                        idempotencyKey: ({
                          runTabFormState,
                        }: DefinitionMachineContext) =>
                          runTabFormState?.idempotencyKey ?? undefined,
                        idempotencyStrategy: ({
                          runTabFormState,
                        }: DefinitionMachineContext) =>
                          runTabFormState?.idempotencyStrategy ??
                          IdempotencyStrategyEnum.RETURN_EXISTING,
                      },
                    },
                    on: {
                      [DefinitionMachineEventTypes.CHANGE_TAB_EVT]: {
                        actions: ["forwardToRunWorkflowMachine"],
                        cond: "isDifferentTab",
                      },
                      [DefinitionMachineEventTypes.REDIRECT_TO_EXECUTION_PAGE]:
                        {
                          actions: ["redirectToExecutionPage"],
                        },
                    },
                  },
                  dependencies: {
                    tags: ["dependenciesTab"],

                    on: {
                      [DefinitionMachineEventTypes.CHANGE_TAB_EVT]: {
                        actions: ["changeTab", "gtagEventLogger"],
                        cond: "isDifferentTab",
                        target: "tabFocus",
                      },
                      [DefinitionMachineEventTypes.REDIRECT_TO_EXECUTION_PAGE]:
                        {
                          actions: ["redirectToExecutionPage"],
                        },
                    },
                  },
                  tabFocusAfter: {
                    // always type transitions wont target an actual transition. If condition did not change
                    // We want the transition to happen to get a re-render
                    after: {
                      50: { target: "tabFocus" },
                    },
                  },
                  workflowEditor: {
                    invoke: {
                      src: workflowMetadataMachine,
                      id: "workflowTabMetaEditor",
                      data: {
                        metadata: (context: DefinitionMachineContext) =>
                          extractWorkflowMetadata(context.workflowChanges!),
                        metadataChanges: (context: DefinitionMachineContext) =>
                          extractWorkflowMetadata(context.workflowChanges!),
                        authHeaders: (context: DefinitionMachineContext) =>
                          context.authHeaders,
                        editableFields: [
                          "inputParameters",
                          "outputParameters",
                          "restartable",
                          "timeoutSeconds",
                          "timeoutPolicy",
                          "failureWorkflow",
                          "name",
                          "description",
                          "inputSchema",
                          "outputSchema",
                          "enforceSchema",
                          "workflowStatusListenerEnabled",
                          "workflowStatusListenerSink",
                          "rateLimitConfig",
                        ],
                        childActorsMachineName: "metadataFieldMachine",
                      },
                    },
                    on: {
                      [DefinitionMachineEventTypes.CHANGE_TAB_EVT]: {
                        actions: ["changeTab", "gtagEventLogger"],
                        cond: "isDifferentTab",
                        target: "tabFocus",
                      },
                      [LocalCopyMachineEventTypes.USE_LOCAL_COPY_WORKFLOW]: {
                        actions: [
                          "forwardWorkflowToTabMetadataEditorMachine",
                          "forwardWorkflowToMetadataEditorMachine",
                        ],
                      },
                      [DefinitionMachineEventTypes.UPDATE_WF_METADATA_EVT]: {
                        actions: [
                          "updateWFMetadata",
                          "validateWorkflow",
                          "notifyToFlowIfOutputParameters",
                          "gtagEventLogger",
                        ], // Will notify flow only if outputParameters were modified, else log
                      },
                      [DefinitionMachineEventTypes.RESET_CONFIRM_EVT]: {
                        actions: [
                          "sendWorkflowChangesToMetadataMachine",
                          "cleanLocalCopyMessage",
                          "gtagEventLogger",
                        ],
                      },
                    },
                  },
                  tabFocus: {
                    always: [
                      { target: "codeEditor", cond: "isEditorTab" },
                      { target: "taskEditor", cond: "isTaskEditorTab" },
                      {
                        target: "workflowEditor",
                        cond: "isWorkflowEditorTab",
                      },
                      { target: "runWorkflow", cond: "isRunTab" },
                      { target: "dependencies", cond: "isDependenciesTab" },
                    ],
                  },
                  confirmReset: {
                    on: {
                      [DefinitionMachineEventTypes.RESET_CONFIRM_EVT]: {
                        actions: [
                          "resetChanges", // Go back to old version
                          "cleanServerErrors", // Clean server errors
                          "removeLocalCopy", // Tell actor to remove local copy
                          "sendWorkflowToInspector", // Tell actor what the new workflow is
                          "notifyFlowUpdates", // Tell actor to redo the nodes for the new changes
                          "notifyFlowResetZoomPosition", // Tell actor to reset zoom, position of diagram
                          "startRenderingGtag",
                          "cleanLocalCopyMessage",
                        ],
                        target: "tabFocus",
                      },
                      [DefinitionMachineEventTypes.CANCEL_EVENT_EVT]: {
                        target: "tabFocus",
                        actions: ["gtagEventLogger"],
                      },
                    },
                  },
                  saveRunRequest: {
                    tags: ["saveRequest"],
                    initial: "confirmSave",
                    states: {
                      confirmSave: {
                        entry: ["setFlowAsReadOnly"],
                        invoke: {
                          src: "saveMachine",
                          id: "saveChangesMachine",
                          onDone: {},
                          onError: {
                            target:
                              "#workflowDefinitionMachine.ready.rightPanel.opened.tabFocus",
                          },
                          data: {
                            editorChanges: ({
                              workflowChanges,
                            }: DefinitionMachineContext) =>
                              JSON.stringify(workflowChanges, null, 2),
                            workflowName: ({
                              workflowName,
                            }: DefinitionMachineContext) => workflowName,
                            currentVersion: ({
                              currentVersion,
                            }: DefinitionMachineContext) => currentVersion,
                            isNewWorkflow: ({
                              isNewWorkflow,
                            }: DefinitionMachineContext) => isNewWorkflow,
                            currentWf: ({
                              currentWf,
                            }: DefinitionMachineContext) => currentWf,
                            authHeaders: ({
                              authHeaders,
                            }: DefinitionMachineContext) => authHeaders,
                            errorInspectorMachine: ({
                              errorInspectorMachine,
                            }: DefinitionMachineContext) =>
                              errorInspectorMachine,
                            isNewVersion: (
                              __context: DefinitionMachineContext,
                              { isNewVersion }: { isNewVersion: boolean },
                            ) => isNewVersion,
                            isContinueCreate: (
                              __context: DefinitionMachineContext,
                              {
                                originalEvent,
                              }: { originalEvent: DefinitionMachineEventTypes },
                            ) =>
                              originalEvent ===
                              DefinitionMachineEventTypes.HANDLE_SAVE_AND_CREATE_NEW,
                          },
                        },
                        on: {
                          [SaveWorkflowMachineEventTypes.SAVED_CANCELLED]: {
                            target:
                              "#workflowDefinitionMachine.ready.rightPanel.opened.tabFocus",
                            actions: [
                              "changeToPreviousTab",
                              "persistWorkflowChanges",
                              "notifyFlowUpdates",
                            ],
                          },
                          [SaveWorkflowMachineEventTypes.SAVED_SUCCESSFUL]: {
                            target:
                              "#workflowDefinitionMachine.ready.rightPanel.opened.runWorkflow",
                            actions: [
                              "showSuccessMassage",
                              "cleanLastOperation",
                              "persistWorkflowNameAndVersion",
                              "cleanLocalCopyMessage", // Note push to history has a callback event since the url will change.
                              "cleanInitialSelectedTaskReferenceName",
                              "justExecute",
                            ],
                          },
                        },
                      },
                    },
                  },
                  saveRequest: {
                    tags: ["saveRequest"],
                    initial: "confirmSave",
                    states: {
                      confirmSave: {
                        entry: ["setFlowAsReadOnly"],
                        invoke: {
                          src: "saveMachine",
                          id: "saveChangesMachine",
                          onDone: {},
                          onError: {
                            target:
                              "#workflowDefinitionMachine.ready.rightPanel.opened.tabFocus",
                          },
                          data: {
                            editorChanges: ({
                              workflowChanges,
                            }: DefinitionMachineContext) =>
                              JSON.stringify(workflowChanges, null, 2),
                            workflowName: ({
                              workflowName,
                            }: DefinitionMachineContext) => workflowName,
                            currentVersion: ({
                              currentVersion,
                            }: DefinitionMachineContext) => currentVersion,
                            isNewWorkflow: ({
                              isNewWorkflow,
                            }: DefinitionMachineContext) => isNewWorkflow,
                            currentWf: ({
                              currentWf,
                            }: DefinitionMachineContext) => currentWf,
                            authHeaders: ({
                              authHeaders,
                            }: DefinitionMachineContext) => authHeaders,
                            errorInspectorMachine: ({
                              errorInspectorMachine,
                            }: DefinitionMachineContext) =>
                              errorInspectorMachine,
                            isNewVersion: (
                              __context: DefinitionMachineContext,
                              { isNewVersion }: { isNewVersion: boolean },
                            ) => isNewVersion,
                            isContinueCreate: (
                              __context: DefinitionMachineContext,
                              {
                                originalEvent,
                              }: { originalEvent: DefinitionMachineEventTypes },
                            ) =>
                              originalEvent ===
                              DefinitionMachineEventTypes.HANDLE_SAVE_AND_CREATE_NEW,
                          },
                        },
                        on: {
                          [SaveWorkflowMachineEventTypes.CANCEL_SAVE_EVT]: {
                            actions: "forwardToSaveMachine",
                          },
                          [SaveWorkflowMachineEventTypes.SAVED_CANCELLED]: {
                            target:
                              "#workflowDefinitionMachine.ready.rightPanel.opened.tabFocus",
                            actions: [
                              "changeToPreviousTab",
                              "persistWorkflowChanges",
                              "notifyFlowUpdates",
                              "startRenderingGtag",
                            ],
                          },
                          [SaveWorkflowMachineEventTypes.SAVED_SUCCESSFUL]: {
                            target: "#workflowDefinitionMachine.presentEditor",
                            actions: [
                              "showSuccessMassage",
                              "cleanLastOperation",
                              "changeToPreviousTab",
                              "persistWorkflowNameAndVersion",
                              "cleanLocalCopyMessage", // Note push to history has a callback event since the url will change.
                              "cleanInitialSelectedTaskReferenceName",
                              "pushToHistory",
                              "raiseUpdateAtribsEvent",
                            ],
                          },
                        },
                      },
                    },
                  },
                  confirmDelete: {
                    on: {
                      [DefinitionMachineEventTypes.DELETE_CONFIRM_EVT]: {
                        target: "deleteWorkflow",
                      },
                      [DefinitionMachineEventTypes.CANCEL_EVENT_EVT]: {
                        target: "tabFocus",
                      },
                    },
                  },
                },
                on: {
                  [DefinitionMachineEventTypes.SAVE_EVT]: [
                    {
                      cond: "isDescriptionEmpty",
                      actions: ["fireChangeToWorkflowTab"],
                    },
                    {
                      cond: "isSaveAndRunWithNoChanges",
                      target: ".runWorkflow",
                      actions: ["justExecute"],
                      // actions: ["fireChangeToRunTab"],
                    },
                    {
                      cond: "isSaveAndRunRequest",
                      target: ".saveRunRequest",
                      actions: ["changeToCodeTab"],
                    },
                    {
                      target: ".saveRequest",
                      actions: ["changeToCodeTab", "gtagEventLogger"],
                    },
                  ],
                  [DefinitionMachineEventTypes.HANDLE_SAVE_AND_RUN]: {
                    actions: ["fireSaveEvent"],
                  },
                  [DefinitionMachineEventTypes.HANDLE_SAVE_AND_CREATE_NEW]: {
                    actions: [
                      "setSaveSourceEventType",
                      "fireSaveAndCreateNewRequestEvent",
                    ],
                  },
                  [DefinitionMachineEventTypes.RESET_EVT]: {
                    target: ".confirmReset",
                    actions: ["gtagEventLogger"],
                  },
                  [DefinitionMachineEventTypes.DELETE_EVT]: {
                    target: ".confirmDelete",
                    actions: ["gtagEventLogger"],
                  },
                  [DefinitionMachineEventTypes.CHANGE_VERSION_EVT]: {
                    actions: ["setVersion", "pushToHistory", "gtagEventLogger"],
                  },
                  [DefinitionMachineEventTypes.ASSIGN_MESSAGE_EVT]: {
                    actions: ["setMessage", "gtagEventLogger"],
                  },
                  [DefinitionMachineEventTypes.MESSAGE_RESET_EVT]: {
                    actions: ["resetMessage", "gtagEventLogger"],
                  },
                  [DefinitionMachineEventTypes.REMOVE_TASK_EVT]: {
                    actions: [
                      "removeTask",
                      "cleanTaskCrumbSelection",
                      "notifyFlowUpdates",
                      "changeToPreviousTab",
                      "startRenderingGtag",
                      "removeQueryParam",
                    ],
                    target: ".tabFocus",
                  },
                  [CodeMachineEventTypes.HIGHLIGHT_TEXT_REFERENCE]: {
                    actions: ["persistCodeReference", "fireChangeToCodeTab"],
                  },
                  [DefinitionMachineEventTypes.HANDLE_LEFT_PANEL_EXPANDED]: {
                    target: "closed",
                    cond: (_, data: any) => (data?.onSelectNode ? false : true),
                  },
                },
              },
              closed: {
                on: {
                  [DefinitionMachineEventTypes.HANDLE_LEFT_PANEL_EXPANDED]: {
                    target: "opened",
                  },
                  [DefinitionMachineEventTypes.RESET_EVT]: {
                    actions: ["raiseResetEvent"],
                    target: "opened",
                  },
                  [DefinitionMachineEventTypes.DELETE_EVT]: {
                    actions: ["raiseDeleteEvent"],
                    target: "opened",
                  },
                  [DefinitionMachineEventTypes.SAVE_EVT]: {
                    actions: ["raiseSaveEvent"],
                    target: "opened",
                  },
                  [DefinitionMachineEventTypes.HANDLE_SAVE_AND_RUN]: {
                    actions: ["raiseSaveAndRunEvent"],
                    target: "opened",
                  },
                  [DefinitionMachineEventTypes.HANDLE_SAVE_AND_CREATE_NEW]: {
                    actions: [
                      "setSaveSourceEventType",
                      "raiseSaveAndCreateNewEvent",
                    ],
                    target: "opened",
                  },
                  [DefinitionMachineEventTypes.CHANGE_VERSION_EVT]: {
                    actions: ["setVersion", "pushToHistory", "gtagEventLogger"],
                  },
                },
              },
            },
          },
          diagram: {
            entry: "notifyFlowUpdates",
            initial: "rendered",
            states: {
              validateAndNotifyUpdates: {
                entry: ["validateWorkflow"],
                on: {
                  [ErrorInspectorEventTypes.WORKFLOW_WITH_NO_ERRORS]: {
                    actions: ["notifyFlowUpdates", "startRenderingGtag"],
                    cond: "workflowWasSentWithNoErrors",
                    target: "rendered",
                  },
                  [ErrorInspectorEventTypes.WORKFLOW_HAS_ERRORS]: {
                    actions: ["setFlowAsReadOnly"],
                    cond: "workflowWasSentWithNoErrors",
                    target: "rendered",
                  },
                },
              },
              rendered: {
                on: {
                  [DefinitionMachineEventTypes.REMOVE_BRANCH_EVT]: {
                    actions: [
                      "persistRemovalOperation",
                      "fireChangeToWorkflowTab",
                      "gtagEventLogger",
                    ],
                    target: "branchRemoval",
                  },
                  [DefinitionMachineEventTypes.ADD_NEW_SWITCH_PATH_EVT]: {
                    actions: ["addNewSwitchStatementToTask", "gtagEventLogger"],
                    target: "validateAndNotifyUpdates",
                  },
                  [DefinitionMachineEventTypes.PERFORM_OPERATION_EVT]: {
                    actions: [
                      "performOperation",
                      "persistLastOperation",
                      "gtagEventLogger",
                    ],
                    target: "validateAndNotifyUpdates",
                  },
                  [ErrorInspectorEventTypes.REPORT_FLOW_ERROR]: {
                    actions: "forwardToErrorInspector",
                  },
                  [DefinitionMachineEventTypes.FLOW_FINISHED_RENDERING]: {
                    actions: [
                      "forwardToErrorInspector",
                      "maybeSelectInitialSelectedTaskReference",
                      "cleanInitialSelectedTaskReferenceName",
                    ],
                  },
                  [DefinitionMachineEventTypes.MOVE_TASK_EVT]: {
                    actions: ["moveTaskFromLocation", "selectMovedTask"],
                    target: "validateAndNotifyUpdates",
                  },
                  [FlowActionTypes.SELECT_NODE_EVT]: {
                    actions: [
                      "persistSelectedTabCrumbs",
                      "changeToTaskTab",
                      "handleLeftPanelExpanded",
                      "setQueryParam",
                    ],
                    cond: "isValidSelection",
                  },
                },
              },
              branchRemoval: {
                // TODO This looks like it could be extracted to a machine
                // if no forks then the forkJoin makes no sense.
                initial: "removalMakesSense",
                states: {
                  removalMakesSense: {
                    always: [
                      {
                        cond: "wantToRemoveLastForkIndex",
                        target: "confirmForkJoinRemoval",
                      },
                      {
                        cond: "selectedTaskIsInForkBranch",
                        target: "switchToWorkflowTab",
                      },
                      {
                        cond: "selectedTaskIsInSwitchBranch",
                        target: "switchToWorkflowTab",
                      },
                      {
                        target: "cleanAndRender",
                      },
                    ],
                  },
                  switchToWorkflowTab: {
                    entry: [
                      "fireChangeToWorkflowTab",
                      "cleanTaskCrumbSelection",
                    ],
                    always: "cleanAndRender",
                  },
                  cleanAndRender: {
                    entry: [
                      "removeBranchFromTask",
                      "cleanLastRemovalOperation",
                      "reSelectTaskIfSelected",
                    ],
                    always:
                      "#workflowDefinitionMachine.ready.diagram.validateAndNotifyUpdates",
                  },
                  confirmForkJoinRemoval: {
                    on: {
                      [DefinitionMachineEventTypes.CONFIRM_LAST_FORK_REMOVAL]: {
                        actions: [
                          "applyLastRemovalOperationAsRemoveTaskOperation",
                          "validateWorkflow",
                          "cleanLastRemovalOperation",
                          "gtagEventLogger",
                        ],
                        target:
                          "#workflowDefinitionMachine.ready.diagram.validateAndNotifyUpdates",
                      },
                      [DefinitionMachineEventTypes.CANCEL_EVENT_EVT]: {
                        actions: ["cleanLastRemovalOperation"],
                        target:
                          "#workflowDefinitionMachine.ready.diagram.rendered",
                      },
                    },
                  },
                },
              },
            },
          },
          localCopiesKeeper: {
            on: {
              [ErrorInspectorEventTypes.WORKFLOW_WITH_NO_ERRORS]: {
                cond: "workflowWasSentWithNoErrors",
                target: ".storeInLocalStorage",
              },
              [LocalCopyMachineEventTypes.REMOVE_LOCAL_COPY_MESSAGE]: {
                actions: ["cleanLocalCopyMessage"],
              },
              [LocalCopyMachineEventTypes.REMOVE_LOCAL_COPY]: {
                target: ".removeWorkflowFromStorage",
              },
            },
            initial: "cleanLocalCopyMessage",
            states: {
              idle: {},
              storeInLocalStorage: {
                invoke: {
                  src: "persistCopyInLocalStorage",
                  onDone: {
                    target: "idle",
                  },
                },
              },
              removeWorkflowFromStorage: {
                invoke: {
                  src: "removeCopyFromStorage",
                  onDone: {
                    target: "idle",
                  },
                },
              },
              cleanLocalCopyMessage: {
                after: {
                  5000: {
                    actions: ["cleanLocalCopyMessage"],
                    target: "idle",
                  },
                },
              },
            },
          },
          fetchForSecrets: {
            initial: "fetchSecretsList",
            states: {
              idle: {},
              fetchSecretsList: {
                invoke: {
                  src: "fetchSecretsEndEnvironmentsList",
                  id: "fetch-secrets-and-envs",
                  onDone: {
                    actions: ["updateSecretsAndEnvs"],
                    target: "idle",
                  },
                  onError: {
                    actions: ["logStuff"],
                    target: "idle",
                  },
                },
              },
            },
          },
          importSuccessfulFlow: {
            initial: "pullImportSummary",
            states: {
              idle: {
                entry: ["dismissImportSuccessfullParam"],
              },
              pullImportSummary: {
                invoke: {
                  src: "fetchForImportedTemplateImportSummary",
                  onDone: [
                    {
                      cond: (_, { data }) => data != null,
                      actions: ["persistImportSummary"],
                      target: "checkFirstTimeFlow",
                    },
                    {
                      target: "checkFirstTimeFlow",
                    },
                  ],
                },
              },
              checkFirstTimeFlow: {
                always: [
                  {
                    cond: "dontNeedToShowImportSuccessfulDialog",
                    target: "idle",
                  },
                  {
                    target: "closeLeftSidebar",
                  },
                ],
              },
              closeLeftSidebar: {
                entry: ["closeLeftSidebar"],
                after: {
                  500: {
                    target: "showImportSuccessfulFlow",
                  },
                },
              },
              showImportSuccessfulFlow: {
                initial: "showCongratsMessage",
                on: {
                  [DefinitionMachineEventTypes.DISMISS_IMPORT_SUCCESSFUL_DIALOG]:
                    {
                      target: "idle",
                    },
                },
                states: {
                  idle: {
                    entry: [
                      "dismissImportSuccessfullParam",
                      "markDontShowImportSuccessfulDialogAgain",
                    ],
                  },
                  showCongratsMessage: {
                    tags: ["showCongratsMessage"],
                    on: {
                      [DefinitionMachineEventTypes.NEXT_STEP_IMPORT_SUCCESSFUL_DIALOG]:
                        {
                          target: "showRunMessage",
                          actions: ["fireChangeToRunTab"],
                        },
                    },
                  },
                  showRunMessage: {
                    tags: ["showRunMessage"],
                    on: {
                      [DefinitionMachineEventTypes.NEXT_STEP_IMPORT_SUCCESSFUL_DIALOG]:
                        [
                          {
                            cond: "importSummaryHasDependencies",
                            target: "showDependenciesTab",
                            actions: ["fireChangeToDependenciesTab"],
                          },
                          {
                            target: "showDescriptionTooltip",
                            actions: [
                              "fireChangeToWorkflowTab",
                              "showTaskDescriptions",
                            ],
                          },
                        ],
                    },
                  },
                  showDependenciesTab: {
                    tags: ["showDependenciesMessage"],
                    on: {
                      [DefinitionMachineEventTypes.NEXT_STEP_IMPORT_SUCCESSFUL_DIALOG]:
                        {
                          target: "showDescriptionTooltip",
                          actions: [
                            "fireChangeToWorkflowTab",
                            "showTaskDescriptions",
                          ],
                        },
                    },
                  },
                  showDescriptionTooltip: {
                    tags: ["showDescriptionTooltip"],
                    on: {
                      [DefinitionMachineEventTypes.NEXT_STEP_IMPORT_SUCCESSFUL_DIALOG]:
                        {
                          actions: ["showTaskDescriptions", "openLeftSidebar"],
                          target: "idle",
                        },
                    },
                  },
                },
              },
            },
          },
          agent: {
            initial: "idle",
            states: {
              idle: {
                on: {
                  [DefinitionMachineEventTypes.WORKFLOW_FROM_AGENT]: {
                    actions: [
                      "persistWorkflowChanges",
                      "notifyFlowUpdatesFromEvent",
                    ],
                    target: "idle",
                  },
                },
              },
            },
          },
        },
        after: {
          5000: {
            actions: ["cleanLocalCopyMessage"],
          },
        },
      },
      errorFetchingWorkflow: {
        on: {
          [DefinitionMachineEventTypes.MESSAGE_RESET_EVT]: {
            actions: ["resetMessage"],
            target: "backToList",
          },
        },
      },
    },
  },
  {
    actions: actions as any,
    services: {
      saveMachine,
      fetchSecretsEndEnvironmentsList,
      persistCopyInLocalStorage,
      removeCopyFromStorage,
      refetchCurrentWorkflowVersionsService,
      fetchInputSchema,
      fetchForImportedTemplateImportSummary,
    },
    guards: guards as any,
  },
);
