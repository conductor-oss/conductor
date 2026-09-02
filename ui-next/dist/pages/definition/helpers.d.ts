import { InlineTaskInputParameters } from "types/TaskType";
import { WorkflowDef, WorkflowMetadataI } from "types/WorkflowDef";
import { TaskDef } from "types/common";
export declare const extractWorkflowMetadata: (workflow: Partial<WorkflowDef>) => Partial<WorkflowMetadataI>;
export declare const undeclaredInputParameters: (inputString: string, taskInputParams?: InlineTaskInputParameters | Record<string, unknown>) => string[];
export declare const invalidDollarVariables: (inputString: string) => string[];
export declare const extractVariablesFromTask: (tasksInCrumbBranch: TaskDef[]) => string[];
