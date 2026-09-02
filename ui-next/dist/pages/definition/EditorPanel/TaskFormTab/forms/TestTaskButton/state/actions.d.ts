import { DoneInvokeEvent } from "xstate";
import { SetTaskDomainEvent, UpdateTaskVariablesEvent, TestTaskButtonMachineContext } from "./types";
import { Execution } from "types/Execution";
export declare const setTaskDomain: import("xstate").AssignAction<TestTaskButtonMachineContext, SetTaskDomainEvent, SetTaskDomainEvent>;
export declare const persistTaskChanges: import("xstate").AssignAction<TestTaskButtonMachineContext, UpdateTaskVariablesEvent, UpdateTaskVariablesEvent>;
export declare const persistExecutionId: import("xstate").AssignAction<TestTaskButtonMachineContext, DoneInvokeEvent<string>, DoneInvokeEvent<string>>;
export declare const persistTestedTaskExecutionResult: import("xstate").AssignAction<TestTaskButtonMachineContext, DoneInvokeEvent<Execution>, DoneInvokeEvent<Execution>>;
