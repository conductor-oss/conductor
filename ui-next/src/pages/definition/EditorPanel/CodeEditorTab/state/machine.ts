import { createMachine } from "xstate";
import * as actions from "./actions";
import {
  CodeMachineContext,
  CodeMachineEventTypes,
  CodeMachineEvents,
} from "./types";

export const codeMachine = createMachine<CodeMachineContext, CodeMachineEvents>(
  {
    predictableActionArguments: true,
    id: "codeDefinitionMachine",
    initial: "editor",
    context: {
      originalWorkflow: {},
      editorChanges: "",
      errorInspectorMachine: undefined,
      tabRequest: undefined,
      referenceText: undefined,
    },
    states: {
      editor: {
        on: {
          [CodeMachineEventTypes.EDIT_EVT]: {
            actions: ["editChanges", "checkForErrorsInWorkflow"],
          },
          [CodeMachineEventTypes.EDIT_DEBOUNCE_EVT]: {
            actions: ["cancelDebounceEditChanges", "debounceEditEvent"],
          },
          [CodeMachineEventTypes.HIGHLIGHT_TEXT_REFERENCE]: {
            actions: ["persistReferenceText"],
          },
          [CodeMachineEventTypes.JUMP_TO_FIRST_ERROR]: {
            target: ".showFirstError",
          },
          [CodeMachineEventTypes.FORCE_WORKFLOW]: {
            actions: ["forceWorkflowChanges"],
          },
        },
        initial: "idle",
        states: {
          idle: {},
          showFirstError: {
            tags: ["showFirstError"],
            after: {
              1000: {
                target: "idle",
              },
            },
          },
        },
      },
    },
  },
  {
    actions: actions as any,
  },
);
