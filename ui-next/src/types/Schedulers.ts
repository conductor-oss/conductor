import { IObject } from "types/common";
import { TagDto } from "./Tag";

export interface IStartWorkflowRequest {
  name: string;
  version: number;
  input?: IObject;
  taskToDomain?: IObject;
  priority?: number;
}

/** One cron expression on a schedule, with the timezone it is read in. */
export interface CronSchedule {
  cronExpression: string;
  zoneId: string;
}

export interface IScheduleDto {
  name: string;
  cronExpression: string;
  /**
   * Several expressions on one schedule. When present and non-empty the server reads these
   * instead of `cronExpression` and `zoneId`.
   */
  cronSchedules?: CronSchedule[];
  zoneId?: string;
  runCatchupScheduleInstances?: boolean;
  paused?: boolean;
  pausedReason?: string;
  active?: boolean;
  startWorkflowRequest?: IStartWorkflowRequest;
  createTime?: number;
  updatedTime?: number;
  createdBy?: string;
  updatedBy?: string;
  nextRunTime?: number;
  tags?: TagDto[];
}

export interface SchedulerSearchResult {
  results: IScheduleDto[];
  totalHits: number;
}
