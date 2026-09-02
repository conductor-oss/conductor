import { TaskDef, Crumb } from "types";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents, OperationContext, PerformedOperation } from "./types";
export type TaskAndCrumbs = {
    task: TaskDef;
    crumbs: Crumb[];
};
export declare const usePerformOperationOnDefinition: (service: ActorRef<WorkflowDefinitionEvents>) => {
    handleReplaceTask: ({ task, crumbs }: TaskAndCrumbs, newTask: TaskDef) => void;
    handleRemoveTask: ({ task, crumbs }: TaskAndCrumbs) => void;
    handleAddSwitchPath: ({ task, crumbs }: TaskAndCrumbs) => void;
    handleRemoveBranch: (removeBranchRelevantData: TaskAndCrumbs & {
        branchName: string;
    }) => void;
    handlePerformOperation: (operationData: {
        data: OperationContext;
        operation: PerformedOperation;
    }) => void;
};
