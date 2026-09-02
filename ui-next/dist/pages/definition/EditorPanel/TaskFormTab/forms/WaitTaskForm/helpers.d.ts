import { WaitTaskDef } from "types";
export declare function durationStringToPairs(duration: string): Array<[string, string]>;
export declare const detectWaitType: (task: WaitTaskDef) => "duration" | "until" | "signal";
