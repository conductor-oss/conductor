import { TaskDef, TaskType } from "./common";

export type Crumb = {
  refIdx: number;
  forkIndex?: number;
  ref: string;
  parent?: string | null;
  decisionBranch?: string;
  type: TaskType;
};

export type CrumbMap = Record<string, { task: TaskDef; crumbs: Crumb[] }>; //Using record for now since xstate inspector wont show Map.
