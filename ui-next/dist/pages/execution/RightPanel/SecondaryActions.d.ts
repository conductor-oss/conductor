import { ExecutionTask } from "types/Execution";
export interface SecondaryActionsProps {
    selectedTask: ExecutionTask;
    containerQueryState: any;
    dynamicForkInstances: any;
}
export declare const SecondaryActions: ({ selectedTask, containerQueryState, dynamicForkInstances, }: SecondaryActionsProps) => any;
