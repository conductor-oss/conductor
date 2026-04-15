import { AlertColor } from "@mui/material";
import { TaskDefinitionDto } from "./TaskDefinition";

export interface IObject {
  [key: string]: any;
}

export type AuthHeaders = Record<string, string | undefined>;

export type HasAuthHeaders = {
  authHeaders: AuthHeaders;
};

export const FIELD_TYPE_STRING = "string";
export const FIELD_TYPE_NUMBER = "number";
export const FIELD_TYPE_OBJECT = "object";
export const FIELD_TYPE_BOOLEAN = "boolean";
export const FIELD_TYPE_NULL = "null";

export type FieldType =
  | typeof FIELD_TYPE_STRING
  | typeof FIELD_TYPE_NUMBER
  | typeof FIELD_TYPE_OBJECT
  | typeof FIELD_TYPE_BOOLEAN
  | typeof FIELD_TYPE_NULL;

export type CoerceToType = "integer" | "double" | "string";

export interface Tag {
  key: string;
  value: string;
  type: "METADATA" | "RATE_LIMIT";
}

export enum TaskType {
  START = "START",
  SIMPLE = "SIMPLE",
  YIELD = "YIELD",
  DYNAMIC = "DYNAMIC",
  FORK_JOIN = "FORK_JOIN",
  FORK_JOIN_DYNAMIC = "FORK_JOIN_DYNAMIC",
  DECISION = "DECISION",
  SWITCH = "SWITCH",
  JOIN = "JOIN",
  DO_WHILE = "DO_WHILE",
  SUB_WORKFLOW = "SUB_WORKFLOW",
  EVENT = "EVENT",
  WAIT = "WAIT",
  USER_DEFINED = "USER_DEFINED",
  HTTP = "HTTP",
  LAMBDA = "LAMBDA",
  INLINE = "INLINE",
  EXCLUSIVE_JOIN = "EXCLUSIVE_JOIN",
  TERMINAL = "TERMINAL",
  TERMINATE = "TERMINATE",
  KAFKA_PUBLISH = "KAFKA_PUBLISH",
  JSON_JQ_TRANSFORM = "JSON_JQ_TRANSFORM",
  SET_VARIABLE = "SET_VARIABLE",
  TERMINATE_WORKFLOW = "TERMINATE_WORKFLOW",
  HUMAN = "HUMAN",
  WAIT_FOR_EVENT = "WAIT_FOR_EVENT",
  TASK_SUMMARY = "TASK_SUMMARY",
  BUSINESS_RULE = "BUSINESS_RULE",
  SENDGRID = "SENDGRID",
  WAIT_FOR_WEBHOOK = "WAIT_FOR_WEBHOOK",
  START_WORKFLOW = "START_WORKFLOW",
  HTTP_POLL = "HTTP_POLL",
  JDBC = "JDBC",
  SWITCH_JOIN = "SWITCH_JOIN", // Pseudo task. doesn't really exist on the workflow
  IA_TASK = "_ai_tc",
  LLM_TEXT_COMPLETE = "LLM_TEXT_COMPLETE",
  LLM_GENERATE_EMBEDDINGS = "LLM_GENERATE_EMBEDDINGS",
  LLM_GET_EMBEDDINGS = "LLM_GET_EMBEDDINGS",
  LLM_STORE_EMBEDDINGS = "LLM_STORE_EMBEDDINGS",
  LLM_SEARCH_INDEX = "LLM_SEARCH_INDEX",
  LLM_INDEX_DOCUMENT = "LLM_INDEX_DOCUMENT",
  GET_DOCUMENT = "GET_DOCUMENT",
  LLM_INDEX_TEXT = "LLM_INDEX_TEXT",
  UPDATE_SECRET = "UPDATE_SECRET",
  JUMP = "JUMP",
  QUERY_PROCESSOR = "QUERY_PROCESSOR",
  OPS_GENIE = "OPS_GENIE",
  GET_SIGNED_JWT = "GET_SIGNED_JWT",
  UPDATE_TASK = "UPDATE_TASK",
  GET_WORKFLOW = "GET_WORKFLOW",
  LLM_CHAT_COMPLETE = "LLM_CHAT_COMPLETE",
  GRPC = "GRPC",
  MCP = "MCP",
  MCP_REMOTE = "MCP_REMOTE",
  CHUNK_TEXT = "CHUNK_TEXT",
  LIST_FILES = "LIST_FILES",
  PARSE_DOCUMENT = "PARSE_DOCUMENT",
}

export interface TaskDef {
  name: string;
  taskReferenceName: string;
  description: string;
  inputParameters?: IObject;
  decisionCases?: Record<string, TaskDef[]>;
  type: TaskType;
  dynamicTaskNameParam?: string;
  caseValueParam?: string;
  caseExpression?: string;
  scriptExpression?: string;
  dynamicForkTasksParam?: string;
  dynamicForkTasksInputParamName?: string;
  defaultCase?: TaskDef[];
  forkTasks?: Array<TaskDef[]>;
  startDelay: number;
  joinOn: string[];
  sink?: string;
  evaluatorType?: string;
  expression?: string;
  loopConditionType?: string;
  loopCondition?: string;
  optional: boolean;
  defaultExclusiveJoinTask: string[];
  loopOver?: TaskDef[];
  subWorkflowParam?: {
    name?: string;
    version?: number | string;
    workflowDefinition?: string | object;
    idempotencyKey?: string;
    idempotencyStrategy?: string;
    priority?: string | number;
  };
  asyncComplete?: boolean;
  triggerFailureWorkflow?: boolean;
  taskStatus?: string;
  workflowId?: string;
  taskRefName?: string;
  taskId?: string;
  mergeOutput?: boolean;
  taskOutput?: Record<string, string>;
  iteration?: number;
  taskDefinition?: TaskDefinitionDto;
}

export interface TaskDto extends TaskDef {
  executable?: boolean;
  tags?: Tag[];
  createTime?: number;
  ownerEmail?: string;
  inputKeys?: string[];
  outputKeys?: string[];
  timeoutPolicy?: string;
  timeoutSeconds?: number;
  retryCount?: number;
  retryLogic?: boolean;
  retryDelaySeconds?: number;
  responseTimeoutSeconds?: number;
  inputTemplate?: string;
  rateLimitPerFrequency?: string;
  rateLimitFrequencyInSeconds?: number;
}

export interface ErrorObj {
  message?: string;
  severity?: AlertColor;
}

export type TryFn<T> = () => Promise<T>;

export type NullifyValues<T> = {
  [K in keyof T]: T[K] extends object
    ? T[K] extends null | undefined
      ? null
      : NullifyValues<T[K]> | null
    : T[K] extends undefined
      ? undefined
      : T[K] | null;
};
