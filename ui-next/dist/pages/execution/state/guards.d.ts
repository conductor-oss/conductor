import { ExecutionMachineContext } from "./types";
import { DoneInvokeEvent } from "xstate";
export declare const canWorkflowChangeState: (context: ExecutionMachineContext) => boolean;
export declare const isExecutionTerminated: (context: ExecutionMachineContext) => boolean;
export declare const isExecutionCompleted: (context: ExecutionMachineContext) => boolean;
export declare const isExecutionFailed: (context: ExecutionMachineContext) => boolean;
export declare const isExecutionTimedOut: (context: ExecutionMachineContext) => boolean;
export declare const isExecutionPaused: (context: ExecutionMachineContext) => boolean;
export declare const isTaskListTab: ({ currentTab }: ExecutionMachineContext) => boolean;
export declare const isAgentExecutionTab: ({ currentTab }: ExecutionMachineContext) => boolean;
/**
 * Gate for the "Agent Execution" debugger tab — only agent-classified
 * executions (Conductor-Agents-compiled workflows) get the tab/default-tab treatment;
 * regular workflows keep Diagram as the default view.
 */
export declare const isAgentWorkflowExecution: (context: ExecutionMachineContext) => boolean;
export declare const isTimeLineTab: ({ currentTab }: ExecutionMachineContext) => boolean;
export declare const isTimeWorkflowInputOutputTab: ({ currentTab, }: ExecutionMachineContext) => boolean;
export declare const isJsonTab: ({ currentTab }: ExecutionMachineContext) => boolean;
export declare const isSummaryTab: ({ currentTab }: ExecutionMachineContext) => boolean;
export declare const isInfinityCountdown: (context: ExecutionMachineContext) => boolean;
export declare const isUseGlobalMessage: (__: ExecutionMachineContext, event: DoneInvokeEvent<{
    originalError: Response;
    errorDetails: {
        message: string;
    };
}>) => boolean;
export declare const isNotFound: (__: ExecutionMachineContext, event: DoneInvokeEvent<{
    originalError: Response;
    errorDetails: {
        message: string;
    };
}>) => boolean;
