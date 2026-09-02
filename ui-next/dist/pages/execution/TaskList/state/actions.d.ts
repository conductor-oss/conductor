import { DoneInvokeEvent } from "xstate";
import { TaskListMachineContext, StatusFilterChangeEvent, TaskListPageResponse, NextPageEvent, ChangeRowsPerPageEvent, SendSelectionToParentEvent } from "./types";
export declare const persistTaskListPage: import("xstate").AssignAction<TaskListMachineContext, DoneInvokeEvent<TaskListPageResponse>, DoneInvokeEvent<TaskListPageResponse>>;
export declare const persistFilterStatus: import("xstate").AssignAction<TaskListMachineContext, StatusFilterChangeEvent, StatusFilterChangeEvent>;
export declare const persistNextPage: import("xstate").AssignAction<TaskListMachineContext, NextPageEvent, NextPageEvent>;
export declare const persistRowPerPage: import("xstate").AssignAction<TaskListMachineContext, ChangeRowsPerPageEvent, ChangeRowsPerPageEvent>;
export declare const selectTask: import("xstate").SendAction<TaskListMachineContext, SendSelectionToParentEvent, import("xstate").AnyEventObject>;
