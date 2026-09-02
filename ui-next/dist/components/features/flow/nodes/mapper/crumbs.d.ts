import { Crumb, TaskDef } from "types";
export declare const crumbsToTaskSteps: (crumbs: Crumb[], tasks: TaskDef[], taskSteps?: TaskDef[], maybeParent?: TaskDef) => TaskDef[];
export declare const crumbsToTask: (crumbs: Crumb[], tasks: TaskDef[]) => TaskDef | undefined;
export declare const removeTaskReferenceFromCrumbs: (crumbs: Crumb[], taskReferenceName: string) => Crumb[];
export declare const isTaskReferenceNestedInAnyTaskReference: (crumbs: Crumb[], targetTaskReference: string, maybeParentTaskReferenceName: string[]) => boolean;
export declare const isTaskReferenceNestedInTaskReference: (crumbs: Crumb[], targetTaskReference: string, maybeParentTaskReferenceName: string) => boolean;
/**
 * Takes the crumb
 * @param crumbs
 * @param forkTaskReferenceName
 * @param joinTaskReferenceName
 */
export declare const isTaskNext: (crumbs: Crumb[], targetTaskReferenceFirst: string, targetTaskReferenceSecond: string) => boolean;
/**
 * Takes a crumbs list and a taskReference. will return the previous task crumb in the DAG tree
 * @param crumbs
 * @param taskReferenceName
 * @returns
 */
export declare const previousTaskCrumb: (crumbs: Crumb[], taskReferenceName: string) => Crumb | undefined;
export declare const isSubWorkflowChild: (crumbs: Crumb[], taskReferenceName: string) => boolean;
