import { ActorRef } from "xstate";
import { TestTaskButtonEvents } from "./types";
export declare const useTestTaskButtonMachine: (actor: ActorRef<TestTaskButtonEvents>) => readonly [{
    readonly originalTask: any;
    readonly taskChanges: any;
    readonly taskDomain: any;
    readonly testedTaskExecutionResult: any;
    readonly testExecutionId: any;
}, {
    readonly setInputParameters: (inputParameters: Record<string, unknown>) => void;
    readonly setTaskDomain: (domain: string) => void;
    readonly handleRunTestTask: () => void;
}];
