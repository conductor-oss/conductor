import { RightPanelContext } from "./types";
export declare const updateTaskState: ({ executionId, selectedTask, authHeaders }: RightPanelContext, event: any) => Promise<any>;
export declare const fetchTaskLogs: ({ authHeaders, selectedTask, }: RightPanelContext) => Promise<any>;
export declare const reRunWoflowFromTask: ({ authHeaders, executionId, selectedTask, }: RightPanelContext) => Promise<any>;
