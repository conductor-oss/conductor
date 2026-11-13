import { TaskStatus } from "./TaskStatus";
import { TaskType, TaskDef } from "./common";
import { WorkflowDef } from "./WorkflowDef";
import { WorkflowTaskMetadata } from "./AgentTaskMetadata";

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
  /** Why native workflow code is held at this task, when it is. */
  held?: string;
  /** The task a held step comes after. */
  pausedOn?: string;
  /**
   * Tasks that ran inside this workflow for this task without being steps of the definition — a
   * guardrail detector, say. Drawn beside the task rather than in the flow, because the workflow
   * never waits on them. See state/detachedTasks.ts.
   */
  sideTasks?: ExecutionTask[];
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
  /**
   * The reference name of the task this one was produced for, when the definition did not produce
   * it on its own: a dynamic fork's children name the fork, and a task scheduled into a running
   * workflow without being a step of it names the task it is running for.
   */
  parentTaskReferenceName?: string;
  taskType: TaskType | string;
  workflowTask: {
    name: string;
    taskReferenceName: string;
    type: string;
    description?: string;
    metadata?: WorkflowTaskMetadata;
  };
  inputData?: T & {
    subWorkflowName?: string;
    integrationName?: string;
    [key: string]: unknown;
  };
  outputData?: {
    subWorkflowId?: string;
    executionId?: string;
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
  /** Native (code-first) workflows: set on a task the workflow code paused on. */
  continuation?: TaskContinuation;
}

/**
 * Where a native workflow's code paused: when the task finishes, the server hands continuation
 * `n` of `lane` to a host of the code, which appends what the code does next after the task.
 */
export interface TaskContinuation {
  lane: string;
  n: number;
  state: "WAITING" | "RUNNABLE" | "LEASED" | "APPLIED";
  epoch?: number;
  worker?: string;
  queuedAt?: number;
  leasedAt?: number;
  appliedAt?: number;
  attempts?: number;
  deferred?: string;
  queue?: string;
  step?: number;
  done?: boolean;
  recorded?: Record<string, unknown>;
  resumedWith?: Record<string, unknown>;
  result?: Record<string, unknown>;
  error?: Record<string, unknown>;
}

/** The engine's state of a native (code-first) workflow execution. */
export interface NativeState {
  codeType?: string;
  continuationTimeoutSeconds?: number;
  step?: number;
  bootstrap?: TaskContinuation;
  handlers?: TaskContinuation;
  replies?: Record<string, unknown>;
  seen?: string[];
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
  /** The task a FAILED workflow failed at, when the server names one. */
  failedTaskId?: string;
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
  /** Native (code-first) workflows only. */
  nativeState?: NativeState;
  workflowIntrospection?: WorkflowIntrospectionRecord[];
  aggregateTokenUsage?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
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
