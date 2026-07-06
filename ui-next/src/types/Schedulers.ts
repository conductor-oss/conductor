import { IObject } from "types/common";
import { TagDto } from "./Tag";

export interface IStartWorkflowRequest {
  name: string;
  version: number;
  input?: IObject;
  taskToDomain?: IObject;
  priority?: number;
}

export interface IScheduleCapabilities {
  update?: boolean;
  delete?: boolean;
  /** Can create a new schedule targeting this row's workflow (clone). */
  create?: boolean;
}

export interface IScheduleDto {
  name: string;
  cronExpression: string;
  runCatchupScheduleInstances?: boolean;
  paused?: boolean;
  pausedReason?: string;
  active?: boolean;
  startWorkflowRequest?: IStartWorkflowRequest;
  createTime?: number;
  updatedTime?: number;
  createdBy?: string;
  updatedBy?: string;
  lastRunTimeInEpoch?: number;
  nextRunTime?: number;
  tags?: TagDto[];
  /** Per-instance capability hints — populated on GET and list responses. */
  capabilities?: IScheduleCapabilities | null;
}

export interface SchedulerSearchResult {
  results: IScheduleDto[];
  totalHits: number;
}
