import { assign, send } from "xstate";
import {
  CodeMachineContext,
  EditEvent,
  DebounceEditEvent,
  CodeMachineEventTypes,
  ForceWorkflowEvent,
  HighlightTextReferenceEvent,
} from "./types";
import { ErrorInspectorEventTypes } from "pages/definition/errorInspector/state/types";
import { cancel } from "xstate/lib/actions";

export const editChanges = assign<CodeMachineContext, EditEvent>({
  editorChanges: (context, { changes }) => changes,
});

export const persistReferenceText = assign<
  CodeMachineContext,
  HighlightTextReferenceEvent
>((context, event) => {
  return {
    referenceText: event.reference,
  };
});

export const debounceEditEvent = send<CodeMachineContext, DebounceEditEvent>(
  (__, { changes }) => ({
    type: CodeMachineEventTypes.EDIT_EVT,
    changes,
  }),
  { delay: 100, id: "debounce_edit_event" },
);

export const checkForErrorsInWorkflow = send<CodeMachineContext, any>(
  ({ editorChanges }) => ({
    type: ErrorInspectorEventTypes.VALIDATE_WORKFLOW_STRING,
    workflowChanges: editorChanges,
  }),
  { to: ({ errorInspectorMachine }) => errorInspectorMachine! },
);

export const cancelDebounceEditChanges = cancel("debounce_edit_event");

export const forceWorkflowChanges = assign<
  CodeMachineContext,
  ForceWorkflowEvent
>({
  editorChanges: (_, { workflow }) => JSON.stringify(workflow, null, 2),
});
