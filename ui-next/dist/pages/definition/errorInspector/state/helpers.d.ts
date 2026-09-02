import { NodeData } from "reaflow";
import { TaskDef, Crumb, CrumbMap, InlineTaskDef, DoWhileTaskDef, JoinTaskDef, SwitchTaskDef, JDBCTaskDef, WorkflowDef } from "types";
import { ServerValidationError, StoredValidationError, ValidationError } from "./types";
import { NodeTaskData } from "components/features/flow/nodes/mapper";
export type NodeInnerData = {
    task: TaskDef;
    crumbs: Crumb[];
};
export declare const nodesToCrumbMap: (nodes: NodeData<NodeInnerData>[]) => CrumbMap;
export declare const validateExpressionWithInputParams: (task: Partial<InlineTaskDef> | Partial<DoWhileTaskDef> | Partial<SwitchTaskDef> | Partial<JoinTaskDef> | Partial<JDBCTaskDef>) => string[] | undefined;
export declare const getVariablesForEachTasks: (crumbMaps: CrumbMap) => Record<string, string[]>;
export declare const jakatraPathToPropertyPath: (path?: string) => string;
export declare const serverValidationErrorToIndexTask: (validationErrors: ServerValidationError[], workflowTasks: TaskDef[]) => StoredValidationError[];
export declare const reverifyServerErrorsTaskChanges: (serverErrors: ValidationError[], currentWorkflow: Partial<WorkflowDef>) => ValidationError[] | undefined;
export declare const filterServerErrorsNotPresentInNodes: (serverErrors: ValidationError[], nodes: NodeData<NodeTaskData<TaskDef>>[]) => {
    validationErrors: StoredValidationError[];
    id: import("./types").ErrorIds;
    message: string;
    hint?: string;
    taskReferenceName?: string;
    path?: string;
    type: import("./types").ErrorTypes;
    severity: import("./types").ErrorSeverity;
    onClickReference?: (data: string) => void;
    taskError?: any;
}[] | undefined;
