import { TestTaskButtonMachineContext } from "./types";
export declare const runTestTask: ({ authHeaders, originalTask, user, taskDomain, taskChanges, tasksList, }: TestTaskButtonMachineContext) => Promise<any>;
export declare const pollForExecutionResult: ({ authHeaders: headers, testExecutionId, }: TestTaskButtonMachineContext) => Promise<any>;
