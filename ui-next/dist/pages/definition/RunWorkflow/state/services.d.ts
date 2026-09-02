import { RunMachineContext } from "./types";
export declare const runWorkflow: ({ authHeaders, input: inputParams, taskToDomain: tasksToDomain, correlationId, currentWf, idempotencyKey, idempotencyStrategy, }: RunMachineContext, __: any) => Promise<any>;
