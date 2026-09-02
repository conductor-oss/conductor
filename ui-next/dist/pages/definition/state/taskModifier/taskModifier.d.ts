import { NodeData, PortData } from "reaflow";
import { Crumb, TaskDef, WorkflowDef } from "types";
export declare const findTaskModificationPath: (crumbs: Crumb[], taskReferenceName: string) => Crumb[];
export declare const applyAddTask: (taskArray: TaskDef[], idx: number, payload: Record<string, unknown> | TaskDef[], crumbProps: {
    onDecisionBranch?: string;
    forkIdx?: number;
}) => never[];
type OperationType = {
    payload: any;
    type: string;
};
export declare const applyOperationArrayOnTasks: (fwCrumb: Crumb[], tasks: TaskDef[], operation?: OperationType) => TaskDef[];
export declare function updateTaskReferenceName(tasks: TaskDef[], oldRef: string, newRef: string): TaskDef[];
type PerformOperationArgs = {
    workflow?: Partial<WorkflowDef>;
    crumbs: Crumb[];
    taskDef: TaskDef;
    operation: OperationType;
};
export declare const performOperation: ({ workflow, crumbs, taskDef: { taskReferenceName }, operation, }: PerformOperationArgs) => {
    tasks: TaskDef[];
    failureWorkflow?: string | undefined;
    schemaVersion?: number | undefined;
    tags?: import("types").Tag[] | undefined;
    inputSchema?: Record<string, unknown> | undefined;
    name?: string | undefined;
    description?: string | undefined;
    version?: number | undefined;
    inputParameters?: string[] | undefined;
    outputParameters?: Record<string, unknown> | undefined;
    restartable?: boolean | undefined;
    timeoutSeconds?: number | undefined;
    timeoutPolicy?: import("types").TimeoutPolicy | undefined;
    ownerEmail?: string | undefined;
    updateTime?: number | undefined;
    workflowStatusListenerEnabled?: boolean | undefined;
    createTime?: number | undefined;
    workflowStatusListenerSink?: string | undefined;
    metadata?: Record<string, unknown> | undefined;
    outputSchema?: Record<string, unknown> | undefined;
    enforceSchema?: boolean | undefined;
};
type TaskAndCrumbs = {
    task: TaskDef;
    crumbs: Crumb[];
};
type MoveTaskArgs = {
    workflow?: Partial<WorkflowDef>;
    source: TaskAndCrumbs;
    target: TaskAndCrumbs;
    position: string;
};
export declare const moveTask: ({ workflow, source: { task: originTaskToMove, crumbs: originCrumbsToMove }, target: { task: belowDestinationTask, crumbs: belowDestinationTaskCrumbs }, position, }: MoveTaskArgs) => {
    tasks: TaskDef[];
    failureWorkflow?: string | undefined;
    schemaVersion?: number | undefined;
    tags?: import("types").Tag[] | undefined;
    inputSchema?: Record<string, unknown> | undefined;
    name?: string | undefined;
    description?: string | undefined;
    version?: number | undefined;
    inputParameters?: string[] | undefined;
    outputParameters?: Record<string, unknown> | undefined;
    restartable?: boolean | undefined;
    timeoutSeconds?: number | undefined;
    timeoutPolicy?: import("types").TimeoutPolicy | undefined;
    ownerEmail?: string | undefined;
    updateTime?: number | undefined;
    workflowStatusListenerEnabled?: boolean | undefined;
    createTime?: number | undefined;
    workflowStatusListenerSink?: string | undefined;
    metadata?: Record<string, unknown> | undefined;
    outputSchema?: Record<string, unknown> | undefined;
    enforceSchema?: boolean | undefined;
};
export declare const buildDataForRemoveBranchOperation: ({ port, node, }: {
    port: PortData;
    node: NodeData;
}) => any;
export declare const buildDataForOperation: (port: PortData & {
    properties: {
        id?: string;
        side: string;
    };
}, { data, ports }: NodeData) => {
    data: any;
};
export declare const positionIdentifier: (position: string) => "ADD_TASK_ABOVE" | "ADD_TASK_BELOW" | "ADD_TASK_IN_DO_WHILE";
export {};
