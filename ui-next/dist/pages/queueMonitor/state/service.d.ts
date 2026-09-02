import { QueueMonitorMachineContext } from "./types";
export declare const fetchForPollData: ({ authHeaders: headers, filterOptions, }: QueueMonitorMachineContext) => Promise<import("./types").FetchResponse>;
export declare const saveOrderAndVisibility: (context: QueueMonitorMachineContext) => Promise<boolean>;
export declare const maybePullOrderAndVisibility: (context: QueueMonitorMachineContext) => Promise<number>;
