import { randomChars as dynamicTaskSuffixGenerator } from "utils";
import {
  BusinessRuleTaskDef,
  DoWhileTaskDef,
  DynamicTaskDef,
  EventTaskDef,
  ForkJoinDynamicDef,
  ForkJoinTaskDef,
  HTTPMethods,
  HttpPollTaskDef,
  HttpTaskDef,
  HumanTaskDef,
  InlineTaskDef,
  JDBCTaskDef,
  JDBCType,
  JoinTaskDef,
  JsonJQTransformTaskDef,
  KafkaPublishTaskDef,
  PollingStrategy,
  SendgridTaskDef,
  SetVariableTaskDef,
  SimpleTaskDef,
  StartWorkflowTaskDef,
  SubWorkflowTaskDef,
  SwitchTaskDef,
  TaskType,
  TerminateTaskDef,
  TerminateWorkflowTaskDef,
  WaitForWebHookTaskDef,
  WaitTaskDef,
  UpdateSecretTaskDef,
  LLMTaskTypes,
  LLMTextCompleteTaskDef,
  LLMGenerateEmbeddings,
  LLMGetEmbeddings,
  LLMStoreEmbeddings,
  LLMIndexDocument,
  LLMSearchIndex,
  LLMIndexText,
  FormTaskType,
  GetDocumentTaskDef,
  QueryProcessorTaskDef,
  QueryProcessorType,
  OpsGenieTaskDef,
  GetSignedJWTTaskDef,
  GetSignedJWTAlgorithmType,
  AssignmentCompletionStrategy,
  UpdateTaskDef,
  GetWorkflowDef,
  LLMChatComplete,
  GrpcTaskDef,
  YieldTaskDef,
  MCPTaskDef,
  ChunkTextTaskDef,
  ListFilesTaskDef,
  ParseDocumentTaskDef,
} from "types";
import { HTTP_TEST_ENDPOINT } from "utils/constants/common";
import { BaseTaskMenuItem } from "./state/types";
import { UpdateTaskStatus } from "types/UpdateTaskStatus";

// THIS FILE SHOULD COME FROM THE SDK

const generateNameAndTaskReference = (
  aParam: string,
  suffixGenerator = dynamicTaskSuffixGenerator,
) => {
  const suffix = suffixGenerator();
  return {
    name: `${aParam}_task_${suffix}`,
    taskReferenceName: `${aParam}_task_${suffix}_ref`,
  };
};

export type NameGeneratorFn = typeof generateNameAndTaskReference;

export interface GenerateTaskNoJoinParams<T> {
  overrides?: Partial<T>;
  nameGenerator?: NameGeneratorFn;
}

type SomeFork = ForkJoinTaskDef | ForkJoinDynamicDef;

export interface GenerateTaskJoinParams extends GenerateTaskNoJoinParams<SomeFork> {
  joinOverrides?: Partial<JoinTaskDef>;
}

export type GenerateTaskFn<T> = T extends SomeFork
  ? (params: GenerateTaskJoinParams) => [T, JoinTaskDef]
  : (params: GenerateTaskNoJoinParams<T>) => T;

const DEFAULT_ARGS = {
  overrides: {},
  joinOverrides: {},
  nameGenerator: generateNameAndTaskReference,
};

export const generateEventTask: GenerateTaskFn<EventTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): EventTaskDef => {
  return {
    ...nameGenerator("event"),
    type: TaskType.EVENT,
    sink: "sqs:internal_event_name",
    inputParameters: {},
    ...overrides,
  };
};

export const generateSimpleTask: GenerateTaskFn<SimpleTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): SimpleTaskDef => {
  return {
    ...nameGenerator("simple"),
    type: TaskType.SIMPLE,
    ...overrides,
  };
};

export const generateYieldTask: GenerateTaskFn<YieldTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): YieldTaskDef => {
  return {
    ...nameGenerator("yield"),
    type: TaskType.YIELD,
    ...overrides,
  };
};

export const generateHTTPTask: GenerateTaskFn<HttpTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): HttpTaskDef => ({
  ...nameGenerator("http"),
  type: TaskType.HTTP,
  inputParameters: {
    uri: HTTP_TEST_ENDPOINT,
    method: HTTPMethods.GET,
    accept: "application/json",
    contentType: "application/json",
    encode: true,
  },
  ...overrides,
});

export const generateGRPCTask: GenerateTaskFn<GrpcTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): GrpcTaskDef => ({
  ...nameGenerator("grpc"),
  type: TaskType.GRPC,
  ...overrides,
});

export const generateMCPTask: GenerateTaskFn<MCPTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): MCPTaskDef => ({
  ...nameGenerator("integration"),
  type: TaskType.MCP,
  ...overrides,
});

export const generateHTTPPollTask: GenerateTaskFn<HttpPollTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): HttpPollTaskDef => ({
  ...nameGenerator("http_poll"),
  type: TaskType.HTTP_POLL,
  inputParameters: {
    http_request: {
      uri: HTTP_TEST_ENDPOINT,
      method: HTTPMethods.GET,
      accept: "application/json",
      contentType: "application/json",
      terminationCondition:
        "(function(){ return $.output.response.body.randomInt > 10;})();",
      pollingInterval: "60",
      pollingStrategy: PollingStrategy.FIXED,
      encode: true,
    },
  },
  ...overrides,
});

export const generateJSONJQTransform: GenerateTaskFn<
  JsonJQTransformTaskDef
> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): JsonJQTransformTaskDef => ({
  ...nameGenerator("json_transform"),
  type: TaskType.JSON_JQ_TRANSFORM,
  inputParameters: {
    persons: [
      {
        name: "some",
        last: "name",
        email: "mail@mail.com",
        id: 1,
      },
      {
        name: "some2",
        last: "name2",
        email: "mail2@mail.com",
        id: 2,
      },
    ],
    queryExpression: ".persons | map({user:{email,id}})",
  },
  ...overrides,
});

export const generateInlineTask: GenerateTaskFn<InlineTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): InlineTaskDef => ({
  ...nameGenerator("inline"),
  type: TaskType.INLINE,
  inputParameters: {
    expression: "(function () {\n  return $.value1 + $.value2;\n})();",
    evaluatorType: "graaljs",
    value1: 1,
    value2: 2,
  },
  ...overrides,
});

export const generateKafkaPublishTask: GenerateTaskFn<KafkaPublishTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): KafkaPublishTaskDef => ({
  ...nameGenerator("kafka_publish"),
  type: TaskType.KAFKA_PUBLISH,
  inputParameters: {
    kafka_request: {
      topic: "userTopic",
      value: "Message to publish",
      bootStrapServers: "localhost:9092",
      headers: {
        "X-Auth": "Auth-key",
      },
      key: "valuekey",
      keySerializer: "org.apache.kafka.common.serialization.IntegerSerializer",
    },
  },
  ...overrides,
});

export const generateJoinTask: GenerateTaskFn<JoinTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): JoinTaskDef => ({
  ...nameGenerator("join"),
  inputParameters: {},
  type: TaskType.JOIN,
  joinOn: [],
  optional: false,
  asyncComplete: false,
  ...overrides,
});

export const generateForkJoinTasks: GenerateTaskFn<ForkJoinTaskDef> = ({
  overrides: forkOverrides = {},
  joinOverrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): [ForkJoinTaskDef, JoinTaskDef] => [
  {
    ...nameGenerator("fork"),
    inputParameters: {},
    type: TaskType.FORK_JOIN,
    defaultCase: [],
    forkTasks: [[]], // TODO check this in the mapper. array of array else it will break
    ...forkOverrides,
  } as ForkJoinTaskDef,
  generateJoinTask({ overrides: joinOverrides, nameGenerator }),
];

export const generateSwitchTasks: GenerateTaskFn<SwitchTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): SwitchTaskDef => ({
  ...nameGenerator("switch"),
  inputParameters: {
    switchCaseValue: "",
  },
  type: TaskType.SWITCH,
  decisionCases: {},
  defaultCase: [],
  evaluatorType: "value-param",
  expression: "switchCaseValue",
  ...overrides,
});

export const generateDoWhileTask: GenerateTaskFn<DoWhileTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): DoWhileTaskDef => ({
  ...nameGenerator("do_while"),
  inputParameters: {},
  type: TaskType.DO_WHILE,
  startDelay: 0,
  optional: false,
  asyncComplete: false,
  loopCondition: "",
  evaluatorType: "value-param",
  loopOver: [],
  ...overrides,
});

export const generateDynamicForkTasks: GenerateTaskFn<ForkJoinDynamicDef> = ({
  overrides: dynamicOverrides = {},
  joinOverrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): [ForkJoinDynamicDef, JoinTaskDef] => [
  {
    ...nameGenerator("fork_join_dynamic"),
    inputParameters: {
      dynamicTasks: "",
      dynamicTasksInput: "",
    },
    type: TaskType.FORK_JOIN_DYNAMIC,
    dynamicForkTasksParam: "dynamicTasks",
    dynamicForkTasksInputParamName: "dynamicTasksInput",
    startDelay: 0,
    optional: false,
    asyncComplete: false,
    ...dynamicOverrides,
  } as ForkJoinDynamicDef,
  {
    ...nameGenerator("join"),
    inputParameters: {},
    type: TaskType.JOIN,
    joinOn: [],
    optional: false,
    asyncComplete: false,
    ...joinOverrides,
  },
];

export const generateWaitTask: GenerateTaskFn<WaitTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): WaitTaskDef => ({
  ...nameGenerator("wait"),
  type: TaskType.WAIT,
  ...overrides,
});

export const generateDynamicTask: GenerateTaskFn<DynamicTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): DynamicTaskDef => ({
  ...nameGenerator("dynamic"),
  inputParameters: {
    taskToExecute: "",
  },
  type: TaskType.DYNAMIC,
  dynamicTaskNameParam: "taskToExecute",
  ...overrides,
});

export const generateTerminateTask: GenerateTaskFn<TerminateTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): TerminateTaskDef => ({
  ...nameGenerator("terminate"),
  inputParameters: {
    terminationStatus: "COMPLETED",
  },
  type: TaskType.TERMINATE,
  startDelay: 0,
  optional: false,
  ...overrides,
});

export const generateSetVariableTask: GenerateTaskFn<SetVariableTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): SetVariableTaskDef => ({
  ...nameGenerator("set_variable"),
  type: TaskType.SET_VARIABLE,
  inputParameters: {
    name: "Orkes",
  },
  ...overrides,
});

export const generateSubWorkflowTask: GenerateTaskFn<SubWorkflowTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): SubWorkflowTaskDef => ({
  ...nameGenerator("sub_workflow"),
  inputParameters: {},
  type: TaskType.SUB_WORKFLOW,
  subWorkflowParam: {
    name: "",
  },
  ...overrides,
});

export const generateTerminateWorkflowTask: GenerateTaskFn<
  TerminateWorkflowTaskDef
> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): TerminateWorkflowTaskDef => ({
  ...nameGenerator("TW"),
  inputParameters: {
    workflowId: [""],
    terminationReason: "",
    triggerFailureWorkflow: false,
  },
  type: TaskType.TERMINATE_WORKFLOW,
  ...overrides,
});

export const generateBusinessRuleTask: GenerateTaskFn<BusinessRuleTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): BusinessRuleTaskDef => ({
  ...nameGenerator("business_rule"),
  inputParameters: {
    ruleFileLocation: "https://business-rules.s3.amazonaws.com/rules.xlsx",
    executionStrategy: "FIRE_FIRST",
    cacheTimeoutMinutes: 60,
    inputColumns: {},
    outputColumns: [],
  },
  type: TaskType.BUSINESS_RULE,
  ...overrides,
});

export const generateSendgridTask: GenerateTaskFn<SendgridTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): SendgridTaskDef => ({
  ...nameGenerator("sendgrid"),
  inputParameters: {
    from: "",
    to: "",
    subject: "",
    contentType: "text/plain",
    content: "",
    sendgridConfiguration: "",
  },
  type: TaskType.SENDGRID,
  ...overrides,
});

export const generateStartWorkflowTask: GenerateTaskFn<
  StartWorkflowTaskDef
> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): StartWorkflowTaskDef => ({
  ...nameGenerator("start_workflow"),
  inputParameters: {
    startWorkflow: {
      name: "",
      input: {},
    },
  },
  type: TaskType.START_WORKFLOW,
  ...overrides,
});

export const generateWaitForWebhookTask: GenerateTaskFn<
  WaitForWebHookTaskDef
> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): WaitForWebHookTaskDef => ({
  ...nameGenerator("webhook"),
  inputParameters: {
    matches: {
      "$['event']['type']": "message",
      "$['event']['text']": "Hello",
    },
  },
  type: TaskType.WAIT_FOR_WEBHOOK,
  ...overrides,
});

export const generateHumanTask: GenerateTaskFn<HumanTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): HumanTaskDef => ({
  ...nameGenerator("human"),
  inputParameters: {
    __humanTaskDefinition: {
      assignmentCompletionStrategy: AssignmentCompletionStrategy.LEAVE_OPEN,
      assignments: [],
    },
  },
  type: TaskType.HUMAN,
  ...overrides,
});

export const generateJDBCTask: GenerateTaskFn<JDBCTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): JDBCTaskDef => {
  return {
    ...nameGenerator("jdbc"),
    inputParameters: {
      integrationName: "",
      statement: "SELECT * FROM tableName WHERE id=?",
      parameters: [],
      type: JDBCType.SELECT,
    },
    type: TaskType.JDBC,
    ...overrides,
  };
};

export const generateChunkTextTask: GenerateTaskFn<ChunkTextTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): ChunkTextTaskDef => ({
  ...nameGenerator("chunk_text"),
  inputParameters: {
    text: "",
    chunkSize: 1024,
    mediaType: "auto",
  },
  type: TaskType.CHUNK_TEXT,
  ...overrides,
});

export const generateParseDocumentTask: GenerateTaskFn<
  ParseDocumentTaskDef
> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): ParseDocumentTaskDef => {
  return {
    ...nameGenerator("parse_document"),
    inputParameters: {
      integrationName: "",
      url: "",
      mediaType: "auto",
      chunkSize: 0,
    },
    type: TaskType.PARSE_DOCUMENT,
    ...overrides,
  };
};

type AILLMTaskTypes =
  | TaskType.LLM_TEXT_COMPLETE
  | TaskType.LLM_GENERATE_EMBEDDINGS
  | TaskType.LLM_GET_EMBEDDINGS
  | TaskType.LLM_STORE_EMBEDDINGS
  | TaskType.LLM_INDEX_DOCUMENT
  | TaskType.LLM_SEARCH_INDEX
  | TaskType.GET_DOCUMENT
  | TaskType.LLM_INDEX_TEXT
  | TaskType.LLM_CHAT_COMPLETE;

export const generateAITask = <T extends LLMTaskTypes>(
  type: AILLMTaskTypes,
) => {
  const taskGen = ({
    overrides = {},
    nameGenerator = generateNameAndTaskReference,
  }): T => {
    const taskProps = {
      ...nameGenerator(type.toLowerCase()),
      inputParameters: {},
      type: type,
      ...overrides,
    };
    const typedProps = {
      [TaskType.LLM_TEXT_COMPLETE]: taskProps as LLMTextCompleteTaskDef,
      [TaskType.LLM_GENERATE_EMBEDDINGS]: taskProps as LLMGenerateEmbeddings,
      [TaskType.LLM_GET_EMBEDDINGS]: taskProps as LLMGetEmbeddings,
      [TaskType.LLM_STORE_EMBEDDINGS]: taskProps as LLMStoreEmbeddings,
      [TaskType.LLM_INDEX_DOCUMENT]: taskProps as LLMIndexDocument,
      [TaskType.LLM_SEARCH_INDEX]: taskProps as LLMSearchIndex,
      [TaskType.LLM_INDEX_TEXT]: taskProps as LLMIndexText,
      [TaskType.GET_DOCUMENT]: taskProps as GetDocumentTaskDef,
      [TaskType.LLM_CHAT_COMPLETE]: taskProps as LLMChatComplete,
    } satisfies Record<AILLMTaskTypes, LLMTaskTypes>;

    return typedProps[type] as T;
  };
  return taskGen as GenerateTaskFn<T>;
};

export const generateUpdateSecretTask: GenerateTaskFn<UpdateSecretTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): UpdateSecretTaskDef => {
  return {
    ...nameGenerator("update_secret"),
    inputParameters: {
      _secrets: {
        secretKey: "my_token",
        secretValue: "input secret value here",
      },
    },
    type: TaskType.UPDATE_SECRET,
    ...overrides,
  };
};

export const generateGetSignedJWTTask: GenerateTaskFn<GetSignedJWTTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): GetSignedJWTTaskDef => {
  return {
    ...nameGenerator("get_signed_jwt"),
    inputParameters: {
      subject: "",
      issuer: "",
      privateKey: "",
      privateKeyId: "",
      audience: "",
      ttlInSecond: 0,
      scopes: [],
      algorithm: GetSignedJWTAlgorithmType.RS256,
    },
    type: TaskType.GET_SIGNED_JWT,
    ...overrides,
  };
};

export const generateQueryProcessorTask: GenerateTaskFn<
  QueryProcessorTaskDef
> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): QueryProcessorTaskDef => {
  return {
    ...nameGenerator("query_processor"),
    inputParameters: {
      workflowNames: [],
      statuses: [],
      correlationIds: [],
      queryType: QueryProcessorType.CONDUCTOR_API,
    },
    type: TaskType.QUERY_PROCESSOR,
    ...overrides,
  };
};

export const generateOpsGenieTask: GenerateTaskFn<OpsGenieTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): OpsGenieTaskDef => {
  return {
    ...nameGenerator("Opsgenie"),
    inputParameters: {
      alias: "",
      description: "",
      visibleTo: [
        {
          id: "id-1",
          type: "type-1",
        },
        {
          id: "id-2",
          type: "type-2",
        },
      ],
      message: "",
      responders: [
        {
          type: "user",
          username: "someone@someone.com",
        },
      ],
      details: {},
    },
    ...overrides,
    type: TaskType.OPS_GENIE,
  };
};

export const generateUpdateTask: GenerateTaskFn<UpdateTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): UpdateTaskDef => {
  return {
    ...nameGenerator("update_task"),
    inputParameters: {
      taskStatus: UpdateTaskStatus.COMPLETED,
      mergeOutput: false,
      workflowId: "${workflow.workflowId}",
      taskRefName: "",
    },
    ...overrides,
    type: TaskType.UPDATE_TASK,
  };
};

export const generateGetWorkflowTask: GenerateTaskFn<GetWorkflowDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): GetWorkflowDef => {
  return {
    ...nameGenerator("get_workflow"),
    inputParameters: {
      id: "",
      includeTasks: false,
    },
    ...overrides,
    type: TaskType.GET_WORKFLOW,
  };
};

export const generateListFilesTask: GenerateTaskFn<ListFilesTaskDef> = ({
  overrides = {},
  nameGenerator = generateNameAndTaskReference,
} = DEFAULT_ARGS): ListFilesTaskDef => ({
  ...nameGenerator("list_files"),
  type: TaskType.LIST_FILES,
  inputParameters: {
    inputLocation: "",
    fileTypes: [],
  },
  ...overrides,
});

export const taskGeneratorMap = {
  [TaskType.WAIT]: generateWaitTask,
  [TaskType.HTTP]: generateHTTPTask,
  [TaskType.KAFKA_PUBLISH]: generateKafkaPublishTask,
  [TaskType.HUMAN]: generateHumanTask,
  [TaskType.BUSINESS_RULE]: generateBusinessRuleTask,
  [TaskType.SENDGRID]: generateSendgridTask,
  [TaskType.WAIT_FOR_WEBHOOK]: generateWaitForWebhookTask,
  [TaskType.HTTP_POLL]: generateHTTPPollTask,
  [TaskType.DO_WHILE]: generateDoWhileTask,
  [TaskType.SIMPLE]: generateSimpleTask,
  [TaskType.YIELD]: generateYieldTask,
  [TaskType.JDBC]: generateJDBCTask,
  [TaskType.EVENT]: generateEventTask,
  [TaskType.JOIN]: generateJoinTask,
  [TaskType.FORK_JOIN]: generateForkJoinTasks,
  [TaskType.FORK_JOIN_DYNAMIC]: generateDynamicForkTasks,
  [TaskType.DYNAMIC]: generateDynamicTask,
  [TaskType.INLINE]: generateInlineTask,
  [TaskType.SWITCH]: generateSwitchTasks,
  [TaskType.JSON_JQ_TRANSFORM]: generateJSONJQTransform,
  [TaskType.TERMINATE]: generateTerminateTask,
  [TaskType.SET_VARIABLE]: generateSetVariableTask,
  [TaskType.TERMINATE_WORKFLOW]: generateTerminateWorkflowTask,
  [TaskType.SUB_WORKFLOW]: generateSubWorkflowTask,
  [TaskType.START_WORKFLOW]: generateStartWorkflowTask,
  [TaskType.LLM_TEXT_COMPLETE]: generateAITask(TaskType.LLM_TEXT_COMPLETE),
  [TaskType.LLM_GENERATE_EMBEDDINGS]: generateAITask(
    TaskType.LLM_GENERATE_EMBEDDINGS,
  ),
  [TaskType.LLM_GET_EMBEDDINGS]: generateAITask(TaskType.LLM_GET_EMBEDDINGS),
  [TaskType.LLM_STORE_EMBEDDINGS]: generateAITask(
    TaskType.LLM_STORE_EMBEDDINGS,
  ),
  [TaskType.LLM_INDEX_DOCUMENT]: generateAITask(TaskType.LLM_INDEX_DOCUMENT),
  [TaskType.LLM_SEARCH_INDEX]: generateAITask(TaskType.LLM_SEARCH_INDEX),
  [TaskType.LLM_INDEX_TEXT]: generateAITask(TaskType.LLM_INDEX_TEXT),
  [TaskType.UPDATE_SECRET]: generateUpdateSecretTask,
  [TaskType.GET_DOCUMENT]: generateAITask(TaskType.GET_DOCUMENT),
  [TaskType.QUERY_PROCESSOR]: generateQueryProcessorTask,
  [TaskType.OPS_GENIE]: generateOpsGenieTask,
  [TaskType.GET_SIGNED_JWT]: generateGetSignedJWTTask,
  [TaskType.UPDATE_TASK]: generateUpdateTask,
  [TaskType.GET_WORKFLOW]: generateGetWorkflowTask,
  [TaskType.LLM_CHAT_COMPLETE]: generateAITask(TaskType.LLM_CHAT_COMPLETE),
  [TaskType.GRPC]: generateGRPCTask,
  [TaskType.MCP]: generateMCPTask,
  [TaskType.CHUNK_TEXT]: generateChunkTextTask,
  [TaskType.LIST_FILES]: generateListFilesTask,
  [TaskType.PARSE_DOCUMENT]: generateParseDocumentTask,
} satisfies Record<FormTaskType, GenerateTaskFn<any>>;

export const uniqueTaskIdGenerator = (sr: BaseTaskMenuItem) => {
  return `${sr.category}-${sr.name}${sr.version ? sr.version : ""}`;
};
