import { ResetZoomPositionEvent, SelectNodeEvent, SelectTaskWithTaskRefEvent, UpdateWfDefinitionEvent } from "components/features/flow/state/types";
import { Execution } from "types";
import { DoneInvokeEvent } from "xstate";
import { ChangeExecutionTabEvent, ClearErrorEvent, CollapseDynamicTaskEvent, ExecutionMachineContext, ExecutionUpdatedEvent, ExpandDynamicTaskEvent, FetchForLogsEvent, PersistErrorEvent, SetDoWhileIterationEvent, ToggleAssistantPanelEvent, UpdateDurationEvent, UpdateExecutionEvent, UpdateSelectedTaskEvent, UpdateVariablesEvent } from "./types";
export declare const updateExecution: import("xstate").AssignAction<ExecutionMachineContext, DoneInvokeEvent<Execution>, DoneInvokeEvent<Execution>>;
export declare const updateExecutionMap: import("xstate").AssignAction<ExecutionMachineContext, DoneInvokeEvent<Execution>, DoneInvokeEvent<Execution>>;
export declare const instanciateFlow: import("xstate").AssignAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const persistExecutionId: import("xstate").AssignAction<ExecutionMachineContext, UpdateExecutionEvent, UpdateExecutionEvent>;
export declare const sendResetZoomEventToFlow: import("xstate").SendAction<ExecutionMachineContext, ResetZoomPositionEvent, any>;
export declare const notifyFlowUpdates: import("xstate").SendAction<ExecutionMachineContext, UpdateWfDefinitionEvent, import("xstate").AnyEventObject>;
export declare const nodeToTaskSelectionToPanel: import("xstate").SendAction<ExecutionMachineContext, SelectNodeEvent, import("xstate").AnyEventObject>;
export declare const taskToTaskSelectionToPanel: import("xstate").SendAction<ExecutionMachineContext, SelectTaskWithTaskRefEvent, import("xstate").AnyEventObject>;
type WrappedErrorMessage = {
    originalError: {
        status: number;
    };
    errorDetails: {
        message: string;
    };
};
export declare const assignError: import("xstate").AssignAction<ExecutionMachineContext, DoneInvokeEvent<WrappedErrorMessage>, DoneInvokeEvent<WrappedErrorMessage>>;
export declare const persistFlowError: import("xstate").AssignAction<ExecutionMachineContext, PersistErrorEvent, PersistErrorEvent>;
export declare const clearError: import("xstate").AssignAction<ExecutionMachineContext, ClearErrorEvent, ClearErrorEvent>;
export declare const addToExpandedDynamic: import("xstate").AssignAction<ExecutionMachineContext, ExpandDynamicTaskEvent, ExpandDynamicTaskEvent>;
export declare const removeFromExpandedDynamic: import("xstate").AssignAction<ExecutionMachineContext, CollapseDynamicTaskEvent, CollapseDynamicTaskEvent>;
export declare const updateWorkflowDefinition: import("xstate").AssignAction<ExecutionMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const persistCurrentTab: import("xstate").AssignAction<ExecutionMachineContext, ChangeExecutionTabEvent, ChangeExecutionTabEvent>;
/**
 * Keeps `context.currentTab` in sync when `initDiagram`'s `always`
 * transitions land on the Agent Execution debugger tab as the *default*
 * tab (no CHANGE_EXECUTION_TAB event involved, so `persistCurrentTab`
 * — which reads `event.tab` — doesn't apply here).
 */
export declare const persistAgentExecutionTab: import("xstate").AssignAction<ExecutionMachineContext, any, any>;
export declare const updateExecutionDuration: import("xstate").AssignAction<ExecutionMachineContext, UpdateDurationEvent, UpdateDurationEvent>;
export declare const gtagEventLogger: (context: ExecutionMachineContext, event: any) => void;
export declare const gtagErrorLogger: (context: ExecutionMachineContext, event: any) => void;
export declare const startRenderingGtag: (context: ExecutionMachineContext, event: any) => void;
export declare const finishRenderingGtag: (context: ExecutionMachineContext, event: any) => void;
export declare const fetchForLogs: import("xstate").SendAction<ExecutionMachineContext, FetchForLogsEvent, import("xstate").AnyEventObject>;
export declare const sendUpdatedExecution: import("xstate").SendAction<ExecutionMachineContext, ExecutionUpdatedEvent, any>;
export declare const forwardSelectionToPanel: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
export declare const raiseExecutionUpdated: import("xstate").RaiseAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const persistSuccessUpdateVariablesMessage: import("xstate").AssignAction<ExecutionMachineContext, DoneInvokeEvent<UpdateVariablesEvent>, DoneInvokeEvent<UpdateVariablesEvent>>;
export declare const persistDoWhileIteration: import("xstate").AssignAction<ExecutionMachineContext, SetDoWhileIterationEvent, SetDoWhileIterationEvent>;
export declare const updateSelectedTask: import("xstate").AssignAction<unknown, UpdateSelectedTaskEvent, UpdateSelectedTaskEvent>;
export declare const toggleAssistantPanel: import("xstate").AssignAction<ExecutionMachineContext, ToggleAssistantPanelEvent, ToggleAssistantPanelEvent>;
export declare const closeAssistantPanel: import("xstate").AssignAction<ExecutionMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const delayedNodeSelection: import("xstate").PureAction<ExecutionMachineContext, import("xstate").EventObject, import("xstate").AnyEventObject>;
export {};
