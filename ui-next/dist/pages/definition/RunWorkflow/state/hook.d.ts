import { ActorRef } from "xstate";
import { RunMachineEvents, FieldsData, IdempotencyStrategyEnum, IdempotencyValuesProp } from "./types";
import { PopoverMessage } from "types/Messages";
export declare const useRunTabActor: (actor: ActorRef<RunMachineEvents>) => readonly [{
    readonly currentWf: Partial<import("../../../..").WorkflowDef>;
    readonly input: string | undefined;
    readonly correlationId: string | undefined;
    readonly taskToDomain: string | undefined;
    readonly isRunning: any;
    readonly popoverMessage: PopoverMessage | null;
    readonly idempotencyKey: string | undefined;
    readonly idempotencyStrategy: IdempotencyStrategyEnum | undefined;
}, {
    readonly handleChangeInputParams: (changes: string) => void;
    readonly handleChangeCorrelationId: (changes: string) => void;
    readonly handleChangeTasksToDomain: (changes: string) => void;
    readonly handleClearForm: () => void;
    readonly handleRunThisWorkflow: () => void;
    readonly handlePopoverMessage: (popoverMessage: PopoverMessage | null) => void;
    readonly handleFillAllFields: (data: FieldsData) => void;
    readonly handleChangeIdempotencyKey: (changes: string) => void;
    readonly handleChangeIdempotencyStrategy: (changes: IdempotencyStrategyEnum) => void;
    readonly handleChangeIdempotencyValues: (changes: IdempotencyValuesProp) => void;
}];
