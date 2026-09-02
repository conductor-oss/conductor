import { DoneInvokeEvent, DoneEvent } from "xstate";
import { ChangeCurrentTabEvent, ClearErrorMessageEvent, RightPanelContext, SetSelectedTaskEvent, SetUpdatedExecutionEvent, UpdateTaskLogsEvent } from "./types";
import { ExecutionTask } from "types";
export declare const persistTaskDetails: import("xstate").AssignAction<RightPanelContext, DoneInvokeEvent<ExecutionTask<{
    forkedTasks: string[];
    forkedTaskDefs: import("types").TaskDef[];
    docLink?: string;
}>>, DoneInvokeEvent<ExecutionTask<{
    forkedTasks: string[];
    forkedTaskDefs: import("types").TaskDef[];
    docLink?: string;
}>>>;
export declare const persistSelectedTask: import("xstate").AssignAction<RightPanelContext, SetSelectedTaskEvent, SetSelectedTaskEvent>;
export declare const notifyTaskUpdateToParent: import("xstate").SendAction<unknown, import("xstate").EventObject, import("xstate").AnyEventObject>;
export declare const notifySelectedTaskUpdateToParent: import("xstate").SendAction<RightPanelContext, SetSelectedTaskEvent, import("xstate").AnyEventObject>;
export declare const sendDoWhileIterationToParent: import("xstate").SendAction<RightPanelContext, DoneEvent, import("xstate").AnyEventObject>;
export declare const sendSelectedTaskToParent: import("xstate").SendAction<RightPanelContext, SetSelectedTaskEvent, import("xstate").AnyEventObject>;
export declare const updateTaskLogs: import("xstate").AssignAction<RightPanelContext, UpdateTaskLogsEvent, UpdateTaskLogsEvent>;
export declare const persistError: import("xstate").AssignAction<RightPanelContext, DoneInvokeEvent<{
    errorDetails: any;
    message: string;
}>, DoneInvokeEvent<{
    errorDetails: any;
    message: string;
}>>;
export declare const clearErrorMessage: import("xstate").AssignAction<RightPanelContext, ClearErrorMessageEvent, ClearErrorMessageEvent>;
export declare const updateCurrentTab: import("xstate").AssignAction<RightPanelContext, ChangeCurrentTabEvent, ChangeCurrentTabEvent>;
export declare const extractUpdates: import("xstate").AssignAction<RightPanelContext, SetUpdatedExecutionEvent, SetUpdatedExecutionEvent>;
