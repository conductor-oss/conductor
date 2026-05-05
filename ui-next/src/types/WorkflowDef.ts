import { TaskDef } from "./common";
import { TagDto } from "./Tag";

export enum TimeoutPolicy {
  RETRY = "RETRY",
  TIME_OUT_WF = "TIME_OUT_WF",
  ALERT_ONLY = "ALERT_ONLY",
}

export interface WorkflowMetadataI {
  name: string;
  description: string;
  version: number;
  inputParameters?: string[];
  outputParameters?: Record<string, unknown>;
  restartable: boolean;
  timeoutSeconds: number;
  timeoutPolicy?: TimeoutPolicy;
  failureWorkflow?: string;
  ownerEmail: string;
  updateTime: number;
  workflowStatusListenerEnabled: boolean;
  createTime?: number;
  workflowStatusListenerSink?: string;
  metadata?: Record<string, unknown>;
  inputSchema?: Record<string, unknown>;
  outputSchema?: Record<string, unknown>;
  enforceSchema?: boolean;
}
export type WorkflowDef = {
  failureWorkflow: string;
  schemaVersion: number;
  tasks: TaskDef[];
  tags?: TagDto[];
  inputSchema?: Record<string, unknown>;
} & WorkflowMetadataI;
