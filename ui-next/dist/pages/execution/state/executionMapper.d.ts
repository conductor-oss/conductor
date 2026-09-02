import { DoWhileSelection, Execution, ExecutionTask, TaskDef } from "types";
import { StatusMap } from "./StatusMapTypes";
import { TaskDefExecutionContext, WorkflowDefExecutionContext } from "./types";
export declare const relatedNamesToTaskDef: (names: string[], executionTasks: ExecutionTask[], parentTaskReferenceName: string) => {};
export declare const executionTasksToStatusMap: (executionTasks?: ExecutionTask[]) => StatusMap;
export declare const taskStatusUpdater: (tasks: TaskDef[] | undefined, statusMap: StatusMap, expandDynamic: string[]) => TaskDefExecutionContext[];
export declare const doWhileSelectionForStatusMap: (doWhileSelection?: DoWhileSelection[], statusMap?: StatusMap) => any;
export declare const executionToWorkflowDef: (execution: Execution, expandDynamic?: never[], doWhileSelection?: DoWhileSelection[], selectedTask?: ExecutionTask) => [WorkflowDefExecutionContext, StatusMap];
