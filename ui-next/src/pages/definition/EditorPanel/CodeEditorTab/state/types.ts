import { ActorRef } from "xstate";
import { WorkflowDef } from "types";
import {
  ErrorInspectorMachineEvents,
  WorkflowWithNoErrorsEvent,
} from "pages/definition/errorInspector/state/types";

export type CodeTextReference = {
  textReference: string;
  referenceReason: "error" | "info";
};

export interface CodeMachineContext {
  originalWorkflow: Partial<WorkflowDef>;
  editorChanges: string;
  errorInspectorMachine?: ActorRef<ErrorInspectorMachineEvents>;
  tabRequest?: number;
  referenceText?: CodeTextReference;
}

export enum CodeMachineEventTypes {
  EDIT_EVT = "EDIT_EVT",
  EDIT_DEBOUNCE_EVT = "EDIT_DEBOUNCE_EVT",
  HIGHLIGHT_TEXT_REFERENCE = "HIGHLIGHT_TEXT_REFERENCE",
  JUMP_TO_FIRST_ERROR = "JUMP_TO_FIRST_ERROR",
  FORCE_WORKFLOW = "FORCE_WORKFLOW",
}

export type EditEvent = {
  type: CodeMachineEventTypes.EDIT_EVT;
  changes: string;
};

export type HighlightTextReferenceEvent = {
  type: CodeMachineEventTypes.HIGHLIGHT_TEXT_REFERENCE;
  reference: CodeTextReference;
};

export type DebounceEditEvent = {
  type: CodeMachineEventTypes.EDIT_DEBOUNCE_EVT;
  changes: string;
};

export type JumpToFirstErrorEvent = {
  type: CodeMachineEventTypes.JUMP_TO_FIRST_ERROR;
};

export type ForceWorkflowEvent = {
  type: CodeMachineEventTypes.FORCE_WORKFLOW;
  workflow: Partial<WorkflowDef>;
};

export type CodeMachineEvents =
  | EditEvent
  | DebounceEditEvent
  | WorkflowWithNoErrorsEvent
  | HighlightTextReferenceEvent
  | JumpToFirstErrorEvent
  | ForceWorkflowEvent;
