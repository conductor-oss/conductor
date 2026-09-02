import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "../state/types";
export declare const useDefinitionMachine: (service: ActorRef<WorkflowDefinitionEvents>) => readonly [{
    readonly handleConfirmReset: () => void;
    readonly handleChangeVersion: (version: string) => void;
    readonly handleConfirmDelete: () => void;
    readonly handleCancelRequest: () => void;
    readonly handleConfirmLastForkRemovalRequest: () => void;
    readonly changeTab: (tab: number) => void;
    readonly setLeftPanelExpanded: () => void;
}, {
    readonly isConfirmDelete: any;
    readonly version: string | undefined;
    readonly versions: number[];
    readonly isConfirmReset: any;
    readonly openedTab: any;
    readonly isSaveRequest: boolean;
    readonly isConfirmingForkRemoval: any;
    readonly leftPanelExpanded: any;
    readonly isRunWorkflow: any;
}];
