import { ExecutedData } from "./Execution";
import { TaskDefinitionDto } from "./TaskDefinition";
import { TaskType } from "./common";

// Copied and fixed from codegen. Use this one.
export enum PollingStrategy {
  FIXED = "FIXED",
  LINEAR_BACKOFF = "LINEAR_BACKOFF",
  EXPONENTIAL_BACKOFF = "EXPONENTIAL_BACKOFF",
}

//  This is copy pasted from the SDK. We should probably use the SDK itself to hit the apis.

export interface CommonTaskDef {
  name: string;
  taskReferenceName: string;
  type: TaskType;
  executionData?: Partial<ExecutedData>;
  taskDefinition?: TaskDefinitionDto;
  description?: string;
  optional?: boolean;
}

export interface JoinTaskDef extends CommonTaskDef {
  type: TaskType.JOIN;
  inputParameters?: Record<string, string>;
  joinOn: string[];
  evaluatorType?: string; // Will change when we get extra details
  expression?: string;
  asyncComplete?: boolean;
}

export interface SwitchTaskDef extends CommonTaskDef {
  inputParameters: Record<string, unknown>;
  type: TaskType.SWITCH;
  decisionCases: Record<string, CommonTaskDef[]>;
  defaultCase: CommonTaskDef[];
  evaluatorType: "value-param" | "javascript";
  expression: string;
}

export enum HTTPMethods {
  GET = "GET",
  HEAD = "HEAD",
  POST = "POST",
  PUT = "PUT",
  PATCH = "PATCH",
  DELETE = "DELETE",
  OPTIONS = "OPTIONS",
  TRACE = "TRACE",
}

export enum GRPCMethodTypes {
  UNARY = "UNARY",
  SERVER_STREAMING = "SERVER_STREAMING",
  CLIENT_STREAMING = "CLIENT_STREAMING",
  BIDIRECTIONAL_STREAMING = "BIDIRECTIONAL_STREAMING",
}

export interface HttpInputParameters {
  uri: string;
  method: HTTPMethods | string;
  accept?: string;
  contentType?: string;
  headers?: Record<string, string>;
  body?: unknown;
  encode?: boolean;
  hedgingConfig?: { maxAttempts?: number };
}

export interface HttpTaskDef extends CommonTaskDef {
  inputParameters:
    | (HttpInputParameters & {
        http_request?: never;
      })
    | { http_request?: HttpInputParameters };
  type: TaskType.HTTP;
  asyncComplete?: boolean;
  optional?: boolean;
}

export interface GrpcTaskDef extends CommonTaskDef {
  type: TaskType.GRPC;
  inputParameters?: {
    service?: string;
    method?: string;
    host?: string;
    port?: number;
    useSSL?: boolean;
    trustCert?: boolean;
    request?: Record<string, unknown>;
    headers?: Record<string, string>;
    inputType?: string;
    methodType?: string;
    outputType?: string;
    hedgingConfig?: { maxAttempts?: number };
  };
}

export interface MCPTaskDef extends CommonTaskDef {
  type: TaskType.MCP;
  inputParameters?: {
    service?: string;
    method?: string;
    useSSL?: boolean;
    trustCert?: boolean;
    request?: Record<string, unknown>;
    headers?: Record<string, string>;
    inputType?: string;
    outputType?: string;
    hedgingConfig?: { maxAttempts?: number };
    integrationType?: string;
  };
}

export interface MCPRemoteTaskDef extends CommonTaskDef {
  type: TaskType.MCP_REMOTE;
  inputParameters?: {
    method?: string;
    url?: string;
    request?: Record<string, unknown>;
    headers?: Record<string, string>;
  };
}

export interface HttpPollTaskDef extends CommonTaskDef {
  inputParameters: {
    [x: string]: unknown;
    http_request: HttpInputParameters & {
      pollingInterval: string;
      pollingStrategy: PollingStrategy;
      terminationCondition: string;
    };
  };
  type: TaskType.HTTP_POLL;
  asyncComplete?: boolean;
}

export interface DoWhileTaskDef extends CommonTaskDef {
  inputParameters: Record<string, unknown>;
  type: TaskType.DO_WHILE;
  startDelay?: number;
  optional?: boolean;
  asyncComplete?: boolean;
  loopCondition: string;
  evaluatorType: string;
  loopOver: CommonTaskDef[];
  keepLastN?: number;
}

export interface SubWorkflowTaskDef extends CommonTaskDef {
  type: TaskType.SUB_WORKFLOW;
  inputParameters?: Record<string, unknown>;
  subWorkflowParam: {
    name: string;
    version?: number;
    taskToDomain?: Record<string, string>;
  };
}

export interface TerminateTaskDef extends CommonTaskDef {
  inputParameters: {
    terminationStatus: "COMPLETED" | "FAILED";
    workflowOutput?: Record<string, string>;
    terminationReason?: string;
  };
  type: TaskType.TERMINATE;
  startDelay?: number;
  optional?: boolean;
}
export interface ForkableTask extends CommonTaskDef {
  forkTasks?: Array<Array<CommonTaskDef>>;
}

export interface ForkJoinTaskDef extends ForkableTask {
  type: TaskType.FORK_JOIN;
  inputParameters?: Record<string, string>;
  forkTasks: Array<Array<CommonTaskDef>>;
  defaultCase: Array<CommonTaskDef>;
}

export interface ForkJoinDynamicDef extends ForkableTask {
  inputParameters: {
    dynamicTasks: any;
    dynamicTasksInput: any;
  };
  type: TaskType.FORK_JOIN_DYNAMIC;
  dynamicForkTasksParam: string; // not string "dynamicTasks",
  dynamicForkTasksInputParamName: string; // not string "dynamicTasksInput",
  startDelay?: number;
  optional?: boolean;
  asyncComplete?: boolean;
}

export interface EventTaskDef extends CommonTaskDef {
  type: TaskType.EVENT;
  sink: string;
  asyncComplete?: boolean;
  optional?: boolean;
  inputParameters?: Record<string, unknown>;
}

export interface SimpleTaskDef extends CommonTaskDef {
  type: TaskType.SIMPLE;
  inputParameters?: Record<string, unknown>;
}

export interface YieldTaskDef extends CommonTaskDef {
  type: TaskType.YIELD;
  inputParameters?: Record<string, unknown>;
}

export interface ContainingQueryExpression {
  queryExpression: string;
  [x: string | number | symbol]: unknown;
}

export interface JsonJQTransformTaskDef extends CommonTaskDef {
  type: TaskType.JSON_JQ_TRANSFORM;
  inputParameters: ContainingQueryExpression;
}

export interface InlineTaskInputParameters {
  evaluatorType: "javascript" | "graaljs";
  expression: string;
  [x: string]: unknown;
}

export interface InlineTaskDef extends CommonTaskDef {
  type: TaskType.INLINE;
  inputParameters: InlineTaskInputParameters;
}

export interface KafkaPublishInputParameters {
  topic: string;
  value: string;
  bootStrapServers: string;
  headers: Record<string, string>;
  key: string | Record<string, unknown>;
  keySerializer: string;
}

export interface KafkaPublishTaskDef extends CommonTaskDef {
  inputParameters: {
    kafka_request: KafkaPublishInputParameters;
  };
  type: TaskType.KAFKA_PUBLISH;
}

export interface DynamicInputParameters {
  taskToExecute?: string;
}

export interface DynamicTaskDef extends CommonTaskDef {
  type: TaskType.DYNAMIC;
  inputParameters: DynamicInputParameters;
  dynamicTaskNameParam: string;
}

export interface SetVariableTaskDef extends CommonTaskDef {
  type: TaskType.SET_VARIABLE;
  inputParameters: Record<string, unknown>;
}

interface TerminateWorkflowParameters {
  workflowId: string[];
  terminationReason: string;
  triggerFailureWorkflow: boolean;
}

export interface TerminateWorkflowTaskDef extends CommonTaskDef {
  type: TaskType.TERMINATE_WORKFLOW;
  inputParameters: TerminateWorkflowParameters;
}

interface BusinessRuleParameters {
  ruleFileLocation: string;
  executionStrategy: string;
  inputColumns: string | Record<string, unknown>;
  outputColumns: string | any[];
  cacheTimeoutMinutes?: number;
}

export interface BusinessRuleTaskDef extends CommonTaskDef {
  type: TaskType.BUSINESS_RULE;
  inputParameters: BusinessRuleParameters;
}

interface SendgridParameters {
  from: string;
  to: string;
  subject: string;
  contentType: string;
  content: string;
  sendgridConfiguration: string;
}

export interface SendgridTaskDef extends CommonTaskDef {
  type: TaskType.SENDGRID;
  inputParameters: SendgridParameters;
}

export interface StartWorkflowInputParameters {
  startWorkflow: {
    name: string;
    input: Record<string, unknown>;
  };
}

export interface StartWorkflowTaskDef extends CommonTaskDef {
  inputParameters: StartWorkflowInputParameters;
}

export interface WaitForWebHookTaskDef extends CommonTaskDef {
  inputParameters: {
    matches: Record<string, unknown> | string;
  };
}

export interface WaitTaskDef extends CommonTaskDef {
  type: TaskType.WAIT;
  inputParameters?: Record<"duration" | "until", string>;
  optional?: boolean;
}

export enum JDBCType {
  SELECT = "SELECT",
  UPDATE = "UPDATE",
}

export enum QueryProcessorType {
  CONDUCTOR_API = "CONDUCTOR_API",
  METRICS = "METRICS",
  CONDUCTOR_EVENTS = "CONDUCTOR_EVENTS",
}

export enum GetSignedJWTAlgorithmType {
  RS256 = "RS256",
}

export interface JDBCInputParameters {
  connectionId?: string; // TODO: will be deprecated
  integrationName: string;
  statement: string;
  parameters: any[];
  expectedUpdateCount?: string;
  type: JDBCType;
}

export const jdbcParameterKeys: Array<keyof JDBCInputParameters> = [
  "connectionId",
  "expectedUpdateCount",
  "integrationName",
  "parameters",
  "statement",
  "type",
];

export interface JDBCTaskDef extends CommonTaskDef {
  type: TaskType.JDBC;
  inputParameters: JDBCInputParameters;
}

export interface UpdateSecretTaskDef extends CommonTaskDef {
  type: TaskType.UPDATE_SECRET;
  inputParameters: {
    _secrets: {
      secretKey: string;
      secretValue: string;
    };
  };
}

export interface GetSignedJWTTaskDef extends CommonTaskDef {
  type: TaskType.GET_SIGNED_JWT;
  inputParameters: {
    subject: string;
    issuer: string;
    privateKey: string;
    privateKeyId: string;
    audience: string;
    ttlInSecond: number;
    scopes: string[];
    algorithm: GetSignedJWTAlgorithmType;
  };
}

export interface QueryProcessorInputParameters {
  workflowNames: string[];
  startTimeFrom?: number;
  startTimeTo?: number;
  correlationIds?: string[];
  freeText?: string;
  statuses: string[];
  queryType: QueryProcessorType;
}
export interface QueryProcessorTaskDef extends CommonTaskDef {
  type: TaskType.QUERY_PROCESSOR;
  inputParameters: QueryProcessorInputParameters;
}

export interface OpsGenieTaskDef extends CommonTaskDef {
  type: TaskType.OPS_GENIE;
  inputParameters: {
    alias: string;
    description: string;
    visibleTo: { id: string; type: string }[];
    details?: {
      [x: string]: string;
    };
    message: string;
    priority?: string;
    responders: { type: string; username: string }[];
    actions?: string[];
    entity?: string;
    token?: string;
    tags?: string[];
  };
}

export interface LLMTextCompleteTaskDef extends CommonTaskDef {
  type: TaskType.LLM_TEXT_COMPLETE;
  inputParameters: {
    llmProvider?: string;
    model?: string;
    promptName?: string;
    promptVariables?: Record<string, unknown>;
    temperature?: number;
    topP?: number;
    maxTokens?: number;
  };
}

export interface LLMGenerateEmbeddings extends CommonTaskDef {
  type: TaskType.LLM_GENERATE_EMBEDDINGS;
  inputParameters: {
    llmProvider?: string;
    model?: string;
    text?: string;
  };
}

export interface LLMGetEmbeddings extends CommonTaskDef {
  type: TaskType.LLM_GET_EMBEDDINGS;
  inputParameters: {
    vectorDB?: string;
    namespace?: string;
    index?: string;
    embedding?: number[];
  };
}

export interface LLMStoreEmbeddings extends CommonTaskDef {
  type: TaskType.LLM_SEARCH_INDEX;
  inputParameters: {
    vectorDB?: string;
    namespace?: string;
    index?: string;
    embeddingModelProvider?: string;
    embeddingModel?: string;
    id?: string;
  };
}

export interface LLMIndexDocument extends CommonTaskDef {
  type: TaskType.LLM_INDEX_DOCUMENT;
  inputParameters: {
    vectorDB?: string;
    namespace?: string;
    index?: string;
    embeddingModelProvider?: string;
    embeddingModel?: string;
    url?: string;
    mediaType?: string;
    chunkSize?: number;
    chunkOverlap?: number;
  };
}

export interface LLMSearchIndex extends CommonTaskDef {
  type: TaskType.LLM_SEARCH_INDEX;
  inputParameters: {
    vectorDB?: string;
    namespace?: string;
    index?: string;
    llmProvider?: string;
  };
}

export interface LLMIndexText extends CommonTaskDef {
  type: TaskType.LLM_INDEX_TEXT;
  inputParameters: {
    vectorDB?: string;
    namespace?: string;
    index?: string;
    embeddingModelProvider?: string;
    embeddingModel?: string;
    docId?: string;
    text?: string;
  };
}

export interface GetDocumentTaskDef extends CommonTaskDef {
  type: TaskType.GET_DOCUMENT;
  inputParameters: {
    url?: string;
    mediaType?: string;
  };
}

export interface UpdateTaskDef extends CommonTaskDef {
  type: TaskType.UPDATE_TASK;
  inputParameters: {
    taskStatus: string;
    workflowId?: string;
    taskRefName?: string;
    taskId?: string;
    mergeOutput: boolean;
    taskOutput?: Record<string, string>;
  };
}

export interface GetWorkflowDef extends CommonTaskDef {
  type: TaskType.GET_WORKFLOW;
  inputParameters: {
    id: string;
    includeTasks: boolean;
  };
}

export interface LLMChatComplete extends CommonTaskDef {
  type: TaskType.LLM_CHAT_COMPLETE;
  inputParameters: {
    llmProvider: string;
    model?: string;
    instructions?: string;
    messages?: string;
  };
}

export interface ChunkTextTaskDef extends CommonTaskDef {
  type: TaskType.CHUNK_TEXT;
  inputParameters: {
    text?: string;
    chunkSize?: number;
    mediaType?: string;
  };
}

export interface ListFilesTaskDef extends CommonTaskDef {
  type: TaskType.LIST_FILES;
  inputParameters: {
    inputLocation: string;
    integrationName?: string;
    outputLocation?: string;
    fileTypes?: string[];
    integrationNames?: Record<string, string>;
  };
}

export interface ParseDocumentTaskDef extends CommonTaskDef {
  type: TaskType.PARSE_DOCUMENT;
  inputParameters: {
    integrationName: string;
    url?: string;
    mediaType?: string;
    chunkSize?: number;
  };
}

export type LLMTaskTypes =
  | LLMGenerateEmbeddings
  | LLMGetEmbeddings
  | LLMIndexDocument
  | LLMSearchIndex
  | LLMIndexText
  | GetDocumentTaskDef
  | LLMTextCompleteTaskDef
  | LLMChatComplete;

export type FormTaskType =
  | TaskType.WAIT
  | TaskType.HTTP
  | TaskType.KAFKA_PUBLISH
  | TaskType.HUMAN
  | TaskType.BUSINESS_RULE
  | TaskType.SENDGRID
  | TaskType.WAIT_FOR_WEBHOOK
  | TaskType.HTTP_POLL
  | TaskType.DO_WHILE
  | TaskType.SIMPLE
  | TaskType.YIELD
  | TaskType.JDBC
  | TaskType.EVENT
  | TaskType.JOIN
  | TaskType.FORK_JOIN
  | TaskType.FORK_JOIN_DYNAMIC
  | TaskType.DYNAMIC
  | TaskType.INLINE
  | TaskType.SWITCH
  | TaskType.JSON_JQ_TRANSFORM
  | TaskType.TERMINATE
  | TaskType.SET_VARIABLE
  | TaskType.TERMINATE_WORKFLOW
  | TaskType.SUB_WORKFLOW
  | TaskType.START_WORKFLOW
  | TaskType.LLM_TEXT_COMPLETE
  | TaskType.LLM_GENERATE_EMBEDDINGS
  | TaskType.LLM_GET_EMBEDDINGS
  | TaskType.LLM_STORE_EMBEDDINGS
  | TaskType.LLM_INDEX_DOCUMENT
  | TaskType.LLM_SEARCH_INDEX
  | TaskType.LLM_INDEX_TEXT
  | TaskType.GET_DOCUMENT
  | TaskType.UPDATE_SECRET
  | TaskType.QUERY_PROCESSOR
  | TaskType.OPS_GENIE
  | TaskType.UPDATE_TASK
  | TaskType.GET_WORKFLOW
  | TaskType.LLM_CHAT_COMPLETE
  | TaskType.GET_SIGNED_JWT
  | TaskType.GRPC
  | TaskType.MCP
  | TaskType.CHUNK_TEXT
  | TaskType.LIST_FILES
  | TaskType.PARSE_DOCUMENT;
