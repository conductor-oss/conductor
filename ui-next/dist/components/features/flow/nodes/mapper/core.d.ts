import { NodeData } from "reaflow";
import { CommonTaskDef, Crumb, WorkflowDef, WorkflowExecutionStatus } from "types";
import { NodeTaskData, EdgeTaskData, SubWorkflowFunction } from "./types";
export declare const extractTaskReferenceName: (tasks: {
    taskReferenceName: string;
}) => unknown[];
type Accumulator = {
    nodes: NodeData<NodeTaskData>[];
    edges: EdgeTaskData[];
    crumbs: Crumb[];
    previousTask?: CommonTaskDef;
    previousTaskAllowsConnection: boolean;
};
type TasksAsNodesProps = {
    tasks?: NodeData<NodeTaskData>[];
    edges?: EdgeTaskData[];
    crumbs?: Crumb[];
    crumbContext?: Partial<Crumb>;
    expandSubWorkflow?: boolean;
    subWorkFlowFetcher?: SubWorkflowFunction;
    readOnly?: boolean;
};
type TaskWalkerFn = (t: CommonTaskDef[], tanProps: TasksAsNodesProps) => Promise<Accumulator>;
export declare const tasksAsNodes: TaskWalkerFn;
export declare const workflowToNodeEdges: (workflow: Partial<WorkflowDef>, showPorts: boolean | undefined, expandSubWorkflow: boolean | undefined, workflowFetcher: SubWorkflowFunction, workflowStatus: WorkflowExecutionStatus) => Promise<{
    nodes: any[];
    edges: any[];
}>;
export {};
