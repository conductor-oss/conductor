import { ExecutionMachineContext, UpdateVariablesEvent } from "./types";
export declare const fetchExecution: (context: ExecutionMachineContext) => Promise<any>;
export declare const restartExecution: ({ executionId, authHeaders }: ExecutionMachineContext, event: any) => Promise<any>;
export declare const retryExecution: ({ executionId, authHeaders }: ExecutionMachineContext, event: any) => Promise<any>;
export declare const terminateExecution: ({ executionId, authHeaders, }: ExecutionMachineContext) => Promise<any>;
export declare const resumeExecution: ({ executionId, authHeaders, }: ExecutionMachineContext) => Promise<any>;
export declare const pauseExecution: ({ executionId, authHeaders, }: ExecutionMachineContext) => Promise<any>;
export declare const updateVariables: ({ executionId, authHeaders }: ExecutionMachineContext, event: UpdateVariablesEvent) => Promise<any>;
