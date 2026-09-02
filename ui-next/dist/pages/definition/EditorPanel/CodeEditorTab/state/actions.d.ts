import { CodeMachineContext, EditEvent, DebounceEditEvent, ForceWorkflowEvent, HighlightTextReferenceEvent } from "./types";
export declare const editChanges: import("xstate").AssignAction<CodeMachineContext, EditEvent, EditEvent>;
export declare const persistReferenceText: import("xstate").AssignAction<CodeMachineContext, HighlightTextReferenceEvent, HighlightTextReferenceEvent>;
export declare const debounceEditEvent: import("xstate").SendAction<CodeMachineContext, DebounceEditEvent, import("xstate").AnyEventObject>;
export declare const checkForErrorsInWorkflow: import("xstate").SendAction<CodeMachineContext, any, import("xstate").AnyEventObject>;
export declare const cancelDebounceEditChanges: import("xstate").CancelAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const forceWorkflowChanges: import("xstate").AssignAction<CodeMachineContext, ForceWorkflowEvent, ForceWorkflowEvent>;
