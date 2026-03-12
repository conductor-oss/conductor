import { ExecutionTask, TaskDef } from "types";

export interface DynamicForkRelations {
  siblings: ExecutionTask[];
  parentTaskReferenceName: string;
}
export interface TypeStatusMap extends ExecutionTask {
  loopOver: ExecutionTask[];
  related: DynamicForkRelations;
  outputData?: Record<string, unknown>;
  parentLoop?: TaskDef;
}

export type StatusMap = Record<string, TypeStatusMap>;
