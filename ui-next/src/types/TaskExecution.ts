import { TaskType } from "types/common";
import { TaskStatus } from "./TaskStatus";

export type TaskExecutionDto = {
  workflowId: string;
  workflowType: string;
  scheduledTime: string;
  startTime: string;
  updateTime: string;
  endTime: string;
  status: TaskStatus;
  executionTime: number;
  queueWaitTime: number;
  taskDefName: string;
  taskType: TaskType;
  input: string;
  output: string;
  taskId: string;
  workflowPriority: number;
};

export type TaskExecutionResult = {
  results: TaskExecutionDto[];
  totalHits: number;
};
