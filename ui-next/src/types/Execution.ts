import { TaskStatus } from "./TaskStatus";
import { TaskType, TaskDef } from "./common";
import { WorkflowDef } from "./WorkflowDef";

export type ExecutedData = {
  status: TaskStatus;
  executed: boolean;
  attempts: number;
  collapsed?: boolean;
  collapsedTasks?: TaskDef[];
  parentTaskReferenceName?: string;
  collapsedTasksStatus?: string[];
  outputData?: Record<string, any>;
  parentLoop?: TaskDef;
};

type ForkedExecutionTaskInputData = {
  forkedTasks: string[];
  forkedTaskDefs: TaskDef[];
  docLink?: string;
};

export interface ExecutionTask<
  T = ForkedExecutionTaskInputData,
> extends TaskDef {
  taskId?: string;
  referenceTaskName: string;
  taskType: TaskType | string;
  workflowTask: {
    name: string;
    taskReferenceName: string;
    type: string;
    description?: string;
  };
  inputData?: T & {
    subWorkflowName?: string;
    integrationName?: string;
    [key: string]: unknown;
  };
  outputData?: {
    subWorkflowId?: string;
    caseOutput?: string[];
    [key: string]: unknown;
  };
  status: TaskStatus;
  executed: boolean;
  domain?: string;
  seq?: string;
  scheduledTime?: number;
  startTime?: number;
  endTime?: number;
  updateTime?: number;
  callbackAfterSeconds?: number;
  pollCount?: number;
  workflowType: string;
  loopOverTask: boolean;
  retryCount?: number;
  reasonForIncompletion?: string;
  workerId?: string;
  correlationId?: string;
  queueWaitTime?: number;
}

// @deprecated use WorkflowExecution instead
export type Execution = {
  tasks: ExecutionTask[];
  workflowDefinition: WorkflowDef;
};

export enum WorkflowExecutionStatus {
  RUNNING = "RUNNING",
  COMPLETED = "COMPLETED",
  FAILED = "FAILED",
  TIMED_OUT = "TIMED_OUT",
  TERMINATED = "TERMINATED",
  PAUSED = "PAUSED",
}

export interface WorkflowExecution {
  tasks: ExecutionTask[];
  workflowDefinition: WorkflowDef;
  correlationId: string;
  createdBy: string;
  endTime: number | string;
  executionTime: number;
  failedReferenceTaskNames: string;
  input: Record<string, unknown>;
  inputSize: number;
  output: Record<string, unknown>;
  outputSize: number;
  priority: number;
  rateLimited?: boolean;
  reasonForIncompletion: string;
  startTime: number | string;
  status: WorkflowExecutionStatus;
  taskToDomain?: Record<string, unknown>;
  updateTime: string;
  version: number;
  workflowId: string;
  workflowName?: string;
  parentWorkflowId?: string;
  parentWorkflowTaskId?: string;
  workflowType: string;
  workflowVersion?: number;
  idempotencyKey?: string;
  event?: string;
  variables?: Record<string, unknown>;
  workflowIntrospection?: WorkflowIntrospectionRecord[];
}

export interface DetailedTime {
  seconds: number;
  nanos: number;
}

export interface WorkflowIntrospectionRecord {
  workflowId: string;
  id: string;
  parentRecordId?: string;
  threadName: string;
  taskId?: string;
  name: string;
  description?: string;
  stacktrace: string;
  start: DetailedTime;
  duration: DetailedTime;
  overhead: DetailedTime;
  attributes?: Record<string, unknown>;
}

export interface WorkflowExecutionSearch {
  queryId: string;
  results: WorkflowExecution[];
}

export type DoWhileSelection = {
  doWhileTaskReferenceName: string;
  selectedIteration: number;
};
