import { NodeData, EdgeData } from "reaflow";
import {
  Crumb,
  CommonTaskDef,
  TaskStatus,
  WorkflowDef,
  ExecutionTask,
} from "types";

export interface NodeTaskData<T extends CommonTaskDef = CommonTaskDef> {
  task: T;
  previousTask?: CommonTaskDef;
  crumbs: Crumb[];
  action?: string;
  status?: TaskStatus;
  originalTask?: T;
  selected?: boolean;
  attempts?: number;
  withinExpandedSubWorkflow?: boolean;
  outputData?: Record<string, unknown>;
  parentLoop?: ExecutionTask;
}

type EdgeInnerData = {
  unreachableEdge?: boolean;
  status?: TaskStatus;
};
export interface NodesAndEdges {
  nodes: NodeData<NodeTaskData>[];
  edges: EdgeData<Partial<EdgeInnerData>>[];
}

export type EdgeTaskData = EdgeData<Partial<EdgeInnerData>>;

export type SubWorkflowFunction = (
  name: string,
  version?: number,
) => Promise<Partial<WorkflowDef>>;
