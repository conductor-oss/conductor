import { JsonSchema, Layout } from "@jsonforms/core";
import { CommonTaskDef } from "./TaskType";
import { TagDto } from "./Tag";

export interface FormRenderProperties {
  jsonSchema?: JsonSchema;
  templateUI?: Layout;
}

export interface TemplateDataType extends FormRenderProperties {
  createTime?: number;
  updateTime?: number;
  createdBy?: string;
  updatedBy?: string;
  name?: string;
  version?: number;
}

export interface HumanTemplate extends FormRenderProperties {
  name: string;
  version: number;
  createdBy?: string;
  createTime?: number;
  updatedBy?: string;
  updateTime?: number;
  tags?: TagDto[];
}

export enum AssigneeType {
  CONDUCTOR_USER = "CONDUCTOR_USER",
  CONDUCTOR_GROUP = "CONDUCTOR_GROUP",
  EXTERNAL_USER = "EXTERNAL_USER",
  EXTERNAL_GROUP = "EXTERNAL_GROUP",
}

export enum TriggerType {
  PENDING = "PENDING",
  ASSIGNED = "ASSIGNED",
  IN_PROGRESS = "IN_PROGRESS",
  COMPLETED = "COMPLETED",
  TIMED_OUT = "TIMED_OUT",
  ASSIGNEE_CHANGED = "ASSIGNEE_CHANGED",
  CLAIMANT_CHANGED = "CLAIMANT_CHANGED",
}

export type TriggerPolicy = {
  startWorkflowRequest: {
    correlationId: string;
    input: Record<string, unknown>;
    name: string;
    taskToDomain: Record<string, string>;
    version: number;
  };
  triggerType?: TriggerType;
};

export enum AssignmentCompletionStrategy {
  LEAVE_OPEN = "LEAVE_OPEN",
  TERMINATE = "TERMINATE",
}

export type HumanTaskAssignee = {
  userType: AssigneeType;
  user: string;
};

export type HumanTaskAssignment = {
  assignee: HumanTaskAssignee;
  slaMinutes?: number;
};

export type HumanTaskDefinition = {
  assignmentCompletionStrategy?: AssignmentCompletionStrategy;
  assignments?: HumanTaskAssignment[];
  userFormTemplate?: Partial<HumanTemplate>;
  taskTriggers?: Array<TriggerPolicy>;
  displayName?: string;
  autoClaim?: boolean;
};

export type HumanTaskInputParams = {
  __humanTaskDefinition: HumanTaskDefinition;
};

export interface HumanTaskDef extends CommonTaskDef {
  inputParameters: HumanTaskInputParams;
}

export enum HumanTaskState {
  IN_PROGRESS = "IN_PROGRESS",
  PENDING = "PENDING",
  ASSIGNED = "ASSIGNED",
  COMPLETED = "COMPLETED",
  TIMED_OUT = "TIMED_OUT",
  DELETED = "DELETED",
}

export type ExecutionHumanTaskDefI = {
  assignments: HumanTaskAssignment[];
  userFormTemplate: {
    name: string;
    version: number;
  };
  fullTemplate?: TemplateDataType;
};

type HumanTaskProcessContext = {
  assigneeIndex: number;
  lastUpdated: number;
  state: HumanTaskState;
};

export type HumanExecutionTask = {
  assignee: HumanTaskAssignee;
  claimant: HumanTaskAssignee;
  createdBy: string;
  createdOn: number;
  definitionName: string;
  humanTaskDef: ExecutionHumanTaskDefI;
  input: HumanTaskInputParams &
    HumanTaskProcessContext &
    Record<string, unknown>;
  taskId: string;
  state: HumanTaskState;
  workflowId: string;
  workflowName: string;
  taskRefName: string;
  output: Record<string, unknown>;
};

export enum SearchType {
  INBOX = "INBOX",
  ADMIN = "ADMIN",
}
export interface HumanTaskSearchQuery {
  assignees?: HumanTaskAssignee[];
  claimants?: HumanTaskAssignee[];
  definitionNames?: string[];
  taskOutputQuery?: string;
  taskInputQuery?: string;
  fullTextQuery?: string;
  states?: TriggerType[];
  taskRefNames?: string[];
  workflowNames?: string[];
  query?: string;
  size: number;
  page: number;
  rowsPerPage: number;
  updateStartTime?: string;
  updateEndTime?: string;
  searchType?: SearchType;
  searchId?: number;
}

export type HumanTaskSearchResponse = {
  totalHits: number;
  results: HumanExecutionTask[];
};
