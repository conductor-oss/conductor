import { UpdateTaskStatus } from "./UpdateTaskStatus";
import {
  GetSignedJWTAlgorithmType,
  HTTPMethods,
  JDBCType,
  QueryProcessorType,
} from "./TaskType";
import { TaskType } from "./common";
import { TimeoutPolicy } from "types/TimeoutPolicy";
import {
  TASK_NAME_REGEX,
  WORKFLOW_NAME_REGEX,
  regexToString,
} from "utils/constants/regex";
import { WORKFLOW_NAME_ERROR_MESSAGE } from "utils/constants/common";

const variablePattern = ".*\\$\\{.*";

export const nameSchema = {
  $id: "/properties/tasks/properties/name",
  type: "string",
  pattern: regexToString(TASK_NAME_REGEX),
  title: "Task name",
  description: "Task name",
  default: "",
};

export const taskReferenceName = {
  $id: "/properties/tasks/properties/taskReferenceName",
  type: "string",
  title: "Task Reference Name",
  minLength: 2,
  description:
    "A unique task reference name for this task in the entire workflow",
};

export const inputParameters = {
  $id: "/properties/tasks/properties/inputParameters",
  anyOf: [
    {
      type: "object",
      properties: {},
      additionalProperties: true,
    },
    {
      type: "string",
      pattern: variablePattern,
    },
  ],
  title: "Input Parameters",
  description: "Task input parameters",
  default: {},
};

export const genericSchema = {
  $id: "/properties/tasks/generic",
  type: "object",
  description: "Generic Schema",
  default: {
    name: "generic_schema",
    taskReferenceName: "generic_schema_ref",
    type: TaskType.USER_DEFINED,
  },
  required: ["type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: {
      type: "string",
      enum: [
        TaskType.DECISION,
        TaskType.START,
        TaskType.USER_DEFINED,
        TaskType.LAMBDA,
        TaskType.TERMINAL,
        TaskType.EXCLUSIVE_JOIN,
        TaskType.WAIT_FOR_EVENT,
        TaskType.IA_TASK,
        TaskType.LLM_TEXT_COMPLETE,
        TaskType.LLM_GENERATE_EMBEDDINGS,
        TaskType.LLM_GET_EMBEDDINGS,
        TaskType.LLM_STORE_EMBEDDINGS,
        TaskType.LLM_SEARCH_INDEX,
        TaskType.LLM_INDEX_DOCUMENT,
        TaskType.GET_DOCUMENT,
        TaskType.LLM_INDEX_TEXT,
        TaskType.JUMP,
        TaskType.LLM_CHAT_COMPLETE,
        TaskType.GRPC,
        TaskType.MCP,
      ],
    },
  },
  additionalProperties: true,
};

export const simpleTaskSchema = {
  $id: "/properties/tasks/simple",
  type: "object",
  description: "Simple task",
  default: {
    name: "simple_task",
    taskReferenceName: "simple_task_ref",
    inputParameters: {},
    type: TaskType.SIMPLE,
  },
  required: ["name", "taskReferenceName", "type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      $ref: inputParameters.$id,
    },
    type: { const: TaskType.SIMPLE },
  },
  additionalProperties: true,
};

export const yieldTaskSchema = {
  $id: "/properties/tasks/yield",
  type: "object",
  description: "Yield task",
  default: {
    name: "yield_task",
    taskReferenceName: "yield_task_ref",
    inputParameters: {},
    type: TaskType.YIELD,
  },
  required: ["name", "taskReferenceName", "type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      $ref: inputParameters.$id,
    },
    type: { const: TaskType.YIELD },
  },
  additionalProperties: true,
};

export const doWhileSchema = {
  $id: "/properties/tasks/doWhile",
  type: "object",
  description: "Do While",
  default: {
    name: "do_while_ref",
    taskReferenceName: "do_while_ref",
    inputParameters: {},
    type: TaskType.DO_WHILE,
  },
  required: [
    "name",
    "taskReferenceName",
    "inputParameters",
    "type",
    "loopOver",
    "loopCondition",
  ],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      $ref: inputParameters.$id,
    },
    loopCondition: {
      type: "string",
    },
    loopOver: {
      $ref: "/properties/tasks",
    },
    type: { const: TaskType.DO_WHILE },
  },
  additionalProperties: true,
};

export const eventTaskSchema = {
  $id: "/properties/tasks/eventTaskSchema",
  type: "object",
  description: "Join task",
  default: {
    name: "join_task_ref",
    taskReferenceName: "join_task_ref",
    inputParameters: {},
    type: TaskType.EVENT,
  },
  required: ["name", "taskReferenceName", "sink", "type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    sink: {
      type: "string",
    },
    inputParameters: {
      $ref: inputParameters.$id,
    },
    type: { const: TaskType.EVENT },
  },
  additionalProperties: true,
};

export const joinTaskSchema = {
  $id: "/properties/tasks/joinTaskSchema",
  type: "object",
  description: "Join task",
  default: {
    name: "join_task_ref",
    taskReferenceName: "join_task_ref",
    inputParameters: {},
    type: TaskType.JOIN,
  },
  required: ["name", "taskReferenceName", "joinOn", "type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    joinOn: {
      type: "array",
      items: {
        type: "string",
      },
    },
    inputParameters: {
      $ref: inputParameters.$id,
    },
    evaluatorType: {
      type: "string",
    },
    expression: {
      type: "string",
    },
    type: { const: TaskType.JOIN },
  },
  additionalProperties: true,
};

export const forkTaskSchema = {
  $id: "/properties/tasks/forkJoin",
  type: "object",
  description: "Fork task",
  default: {
    name: "fork_task_ref",
    taskReferenceName: "fork_task_ref",
    inputParameters: {},
    type: TaskType.FORK_JOIN,
  },
  required: [
    "name",
    "taskReferenceName",
    "inputParameters",
    "type",
    "forkTasks",
  ],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      $ref: inputParameters.$id,
    },
    forkTasks: {
      type: "array",
      default: [[]],
      items: {
        $ref: "/properties/tasks",
      },
    },
    type: { const: TaskType.FORK_JOIN },
  },
  additionalProperties: true,
};

export const waitSchema = {
  $id: "/properties/tasks/wait",
  type: "object",
  description: "Wait task",
  default: {
    name: "wait_ref",
    taskReferenceName: "wait_ref",
    type: TaskType.WAIT,
  },
  required: ["name", "taskReferenceName", "type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      $ref: inputParameters.$id,
    },
    type: { const: TaskType.WAIT },
  },
  additionalProperties: true,
};

export const forkJoinDynamicSchema = {
  $id: "/properties/tasks/forkJoinDynamic",
  type: "object",
  description: "Fork join dynamic task",
  default: {
    name: "fork_join_dynamic_ref",
    taskReferenceName: "fork_join_dynamic_ref",
    inputParameters: {},
    type: TaskType.FORK_JOIN_DYNAMIC,
  },
  required: ["name", "taskReferenceName", "type", "inputParameters"],
  properties: {
    inputParameters: {
      $ref: inputParameters.$id,
    },
    dynamicForkTasksParam: {
      type: "string",
    },
    dynamicForkTasksInputParamName: {
      type: "string",
    },
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.FORK_JOIN_DYNAMIC },
  },
  additionalProperties: true,
};

export const dynamicTaskSchema = {
  $id: "/properties/tasks/dynamic",
  type: "object",
  description: "Dynamic task",
  default: {
    name: "dynamic_ref",
    taskReferenceName: "dynamic_ref",
    type: TaskType.DYNAMIC,
  },
  required: ["name", "taskReferenceName", "type"],
  properties: {
    inputParameters: {
      $ref: inputParameters.$id,
    },
    dynamicTaskNameParam: {
      type: "string",
    },
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.DYNAMIC },
  },
  additionalProperties: true,
};

export const inlineTaskSchema = {
  $id: "/properties/tasks/inlineSchema",
  type: "object",
  description: "Inline task schema",
  default: {
    name: "inline_task_ref",
    taskReferenceName: "inline_task_ref",
    type: TaskType.INLINE,
  },
  required: ["name", "taskReferenceName", "type"],
  properties: {
    inputParameters: {
      anyOf: [
        {
          type: "object",
          required: ["evaluatorType", "expression"],
          properties: {
            evaluatorType: {
              type: "string",
              enum: ["javascript", "graaljs"],
            },
            expression: {
              type: "string",
            },
            additionalProperties: true,
          },
        },
        {
          type: "string",
          pattern: variablePattern,
        },
      ],
    },
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.INLINE },
  },
  additionalProperties: true,
};

export const switchTaskSchema = {
  $id: "/properties/tasks/switchTaskSchema",
  type: "object",
  description: "Switch task schema",
  default: {
    name: "switch_task_ref",
    taskReferenceName: "switch_task_ref",
    type: TaskType.SWITCH,
  },
  required: [
    "name",
    "taskReferenceName",
    "type",
    "evaluatorType",
    "expression",
    "decisionCases",
    "defaultCase",
  ],
  properties: {
    inputParameters: {
      $ref: inputParameters.$id,
    },
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.SWITCH },
    evaluatorType: {
      enum: ["javascript", "value-param", "graaljs"],
      type: "string",
    },
    expression: {
      type: "string",
    },
    decisionCases: {
      type: "object",
      patternProperties: {
        ".*": {
          $ref: "/properties/tasks",
        },
      },
      additionalProperties: true,
    },
    defaultCase: {
      $ref: "/properties/tasks",
    },
  },
  additionalProperties: true,
};

export const kafkaRequestTaskSchema = {
  $id: "/properties/tasks/kafkaRequestSchema",
  type: "object",
  description: "Kafka task",
  default: {
    name: "http_task_ref",
    taskReferenceName: "http_task_ref",
    type: TaskType.KAFKA_PUBLISH,
  },
  required: ["name", "taskReferenceName", "type", "inputParameters"],
  properties: {
    inputParameters: {
      anyOf: [
        {
          type: "object",
          properties: {
            kafka_request: {
              anyOf: [
                {
                  type: "object",
                  properties: {
                    headers: {
                      anyOf: [
                        {
                          type: "object",
                        },
                        {
                          type: "string",
                          pattern: variablePattern,
                        },
                      ],
                    },
                    key: {
                      type: "string",
                    },
                    value: {
                      anyOf: [
                        {
                          type: "object",
                        },
                        {
                          type: "string",
                        },
                        {
                          type: "number",
                        },
                        {
                          type: "boolean",
                        },
                        {
                          type: "array",
                        },
                        {
                          type: "null",
                        },
                      ],
                    },
                  },
                  additionalProperties: true,
                },
                {
                  type: "string",
                  pattern: variablePattern,
                },
              ],
            },
          },
          required: ["kafka_request"],
          additionalProperties: true,
        },
        {
          type: "string",
          pattern: variablePattern,
        },
      ],
    },
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.KAFKA_PUBLISH },
  },
  additionalProperties: true,
};

export const baseHTTPRequestSchema = {
  $id: "/properties/tasks/properties/inputParameters/properties/http_request",
  type: "object",
  properties: {
    uri: {
      type: "string",
    },
    method: {
      type: "string",
      anyOf: [
        {
          enum: Object.values(HTTPMethods),
        },
        {
          pattern: variablePattern,
        },
      ],
    },
    headers: {
      type: ["string", "object"],
      patternProperties: {
        "^\\S*$": { type: "string" },
      },
      additionalProperties: false,
    },
    terminationCondition: {
      type: "string",
    },
    pollingInterval: {
      type: "string",
    },
    pollingStrategy: {
      type: "string",
    },
    encode: {
      type: "boolean",
    },
    additionalProperties: true,
  },

  additionalProperties: true,
};

export const httpTaskSchema = {
  $id: "/properties/tasks/httpTaskSchema",
  type: "object",
  description: "HTTP task",
  default: {
    name: "http_task_ref",
    taskReferenceName: "http_task_ref",
    type: TaskType.HTTP,
  },
  required: ["name", "taskReferenceName", "type", "inputParameters"],
  properties: {
    inputParameters: {
      type: ["object", "string"],
      properties: {
        http_request: {
          anyOf: [
            {
              $ref: baseHTTPRequestSchema.$id,
            },
            {
              type: "string",
              pattern: variablePattern,
            },
          ],
        },
      },
      additionalProperties: true,
    },
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.HTTP },
  },
  additionalProperties: true,
};

export const httpPollTaskSchema = {
  $id: "/properties/tasks/httpPollTaskSchema",
  type: "object",
  description: "HTTP POLL task",
  default: {
    name: "http_poll_task_ref",
    taskReferenceName: "http_poll_task_ref",
    type: TaskType.HTTP_POLL,
  },
  required: ["name", "taskReferenceName", "type", "inputParameters"],
  properties: {
    inputParameters: {
      type: ["object", "string"],
      properties: {
        http_request: {
          anyOf: [
            {
              type: "object",
              $ref: baseHTTPRequestSchema.$id,
              required: [
                "terminationCondition",
                "pollingInterval",
                "pollingStrategy",
              ],
            },
            {
              type: "string",
              pattern: variablePattern,
            },
          ],
        },
      },
      required: ["http_request"],
      additionalProperties: true,
    },
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.HTTP_POLL },
  },
  additionalProperties: true,
};

export const jsonJQTaskSchema = {
  $id: "/properties/tasks/jsonJQTask",
  type: "object",
  description: "JsonJQTask task",
  default: {
    name: "join_jq_task_ref",
    taskReferenceName: "join_jq_task_ref",
    type: TaskType.JSON_JQ_TRANSFORM,
  },
  required: ["name", "taskReferenceName", "type", "inputParameters"],
  properties: {
    inputParameters: {
      anyOf: [
        {
          type: "object",
          properties: {
            queryExpression: {
              type: "string",
            },
          },
          required: ["queryExpression"],
          additionalProperties: true,
        },
        {
          type: "string",
          pattern: variablePattern,
        },
      ],
    },
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.JSON_JQ_TRANSFORM },
  },
  additionalProperties: true,
};

export const terminateTaskSchema = {
  $id: "/properties/tasks/terminate",
  type: "object",
  description: "Terminate Task",
  default: {
    name: "terminate_ref",
    taskReferenceName: "terminate_ref",
    type: TaskType.TERMINATE,
  },
  required: ["name", "taskReferenceName", "type"],
  properties: {
    inputParameters: {
      anyOf: [
        {
          type: "object",
          properties: {
            terminationStatus: {
              enum: ["COMPLETED", "FAILED", "TERMINATED"],
              type: "string",
            },
            workflowOutput: {
              type: "object",
            },
          },
          additionalProperties: true,
          required: ["terminationStatus"],
        },
        {
          type: "string",
          pattern: variablePattern,
        },
      ],
    },
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.TERMINATE },
  },
  additionalProperties: true,
};

export const setVariableTaskSchema = {
  $id: "/properties/tasks/setVariable",
  type: "object",
  description: "Set variable",
  default: {
    name: "set_variable_ref",
    taskReferenceName: "set_variable_ref",
    type: TaskType.SET_VARIABLE,
  },
  required: ["name", "taskReferenceName", "type", "inputParameters"],
  properties: {
    inputParameters: {
      $ref: inputParameters.$id,
    },
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.SET_VARIABLE },
  },
  additionalProperties: true,
};

export const terminateWorkflowSchema = {
  $id: "/properties/tasks/terminateWorkflowSchema",
  type: "object",
  description: "Terminate workflow",
  default: {
    name: "terminate_workflow_schema_ref",
    taskReferenceName: "terminate_workflow_schema_ref",
    inputParameters: {
      workflowId: "someWorkflowId",
    },
    type: TaskType.TERMINATE_WORKFLOW,
  },
  required: ["name", "taskReferenceName", "inputParameters", "type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      anyOf: [
        {
          type: "object",
          required: ["workflowId"],
          properties: {
            workflowId: {
              anyOf: [
                {
                  type: "string",
                },
                {
                  type: "array",
                  items: {
                    type: "string",
                  },
                },
              ],
            },
            terminationReason: {
              type: "string",
            },
          },
          additionalProperties: true,
        },
        {
          type: "string",
          pattern: variablePattern,
        },
      ],
    },
    type: { const: TaskType.TERMINATE_WORKFLOW },
  },
  additionalProperties: true,
};

export const businessRuleSchema = {
  $id: "/properties/tasks/businessRuleSchema",
  type: "object",
  description: "Evaluate business rules that are compiled in a spreadsheet.",
  default: {
    name: "business_rule_schema_ref",
    taskReferenceName: "business_rule_schema_ref",
    inputParameters: {
      ruleFileLocation: "https://business-rules.s3.amazonaws.com/rules.xlsx",
      executionStrategy: "FIRE_FIRST",
      inputColumns: {},
      outputColumns: [],
    },
    type: TaskType.BUSINESS_RULE,
  },
  required: ["name", "taskReferenceName", "inputParameters", "type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      anyOf: [
        {
          type: "object",
          properties: {
            inputColumns: {
              anyOf: [
                {
                  type: "object",
                  additionalProperties: true,
                },
                {
                  type: "string",
                  pattern: variablePattern,
                },
              ],
            },
            outputColumns: {
              anyOf: [
                {
                  type: "array",
                  items: { type: "string" },
                },
                {
                  type: "string",
                  pattern: variablePattern,
                },
              ],
            },
            additionalProperties: true,
          },
        },
        {
          type: "string",
          pattern: variablePattern,
        },
      ],
    },
    type: { const: TaskType.BUSINESS_RULE },
  },
  additionalProperties: true,
};

export const sendgridMailRequestSchema = {
  $id: "/properties/tasks/sendgridSchema/properties/inputParameters",
  type: "object",
  required: ["from", "to", "contentType", "content", "sendgridConfiguration"],
  properties: {
    from: {
      type: "string",
    },
    to: {
      type: "string",
    },
    subject: {
      type: "string",
    },
    contentType: {
      type: "string",
    },
    content: {
      type: "string",
    },
    sendgridConfiguration: {
      type: "string",
    },
  },

  additionalProperties: true,
};

export const sendgridSchema = {
  $id: "/properties/tasks/sendgridSchema",
  type: "object",
  description: "Send email using sendgrid",
  default: {
    name: "sendgrid_task_ref",
    taskReferenceName: "sendgrid_task_ref",
    type: TaskType.SENDGRID,
  },
  required: ["name", "taskReferenceName", "type", "inputParameters"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.SENDGRID },
    inputParameters: { $ref: sendgridMailRequestSchema.$id },
    additionalProperties: true,
  },
};

export const subWorkflowTaskSchema = {
  $id: "/properties/tasks/subWorkflowTask",
  type: "object",
  description: "sub workflow variable",
  default: {
    name: "sub_workflow_ref",
    taskReferenceName: "sub_workflow_ref",
    type: TaskType.SUB_WORKFLOW,
  },
  required: [
    "name",
    "taskReferenceName",
    "type",
    "inputParameters",
    "subWorkflowParam",
  ],
  properties: {
    inputParameters: {
      $ref: inputParameters.$id,
    },
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    subWorkflowParam: {
      type: "object",
      properties: {
        name: {
          type: "string",
        },
        version: {
          type: ["integer", "string"],
        },
        taskToDomain: {
          type: "object",
          additionalProperties: true,
        },
        workflowDefinition: {
          anyOf: [
            {
              type: "string",
            },
            {
              $ref: "/workflow-schema",
            },
          ],
        },
      },
    },
    type: { const: TaskType.SUB_WORKFLOW },
  },
  additionalProperties: true,
};

export const startWorkflowTaskSchema = {
  $id: "/properties/tasks/starkWorkflow",
  type: "object",
  description: "Start Workflow",
  default: {
    name: "start_workflow",
    taskReferenceName: "start_workflow_ref",
    inputParameters: {
      startWorkflow: {
        name: "image_convert_resize",
        input: {},
      },
    },
    type: TaskType.START_WORKFLOW,
  },
  required: ["name", "taskReferenceName", "type", "inputParameters"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      $ref: inputParameters.$id,
    },
    type: { const: TaskType.START_WORKFLOW },
  },
};

export const webhookTaskSchema = {
  $id: "/properties/tasks/webhook",
  type: "object",
  description: "Wait For Webhook Task",
  default: {
    name: "webhook",
    taskReferenceName: "webhook_ref",
    inputParameters: {
      matches: {
        type: "object",
      },
    },
    type: TaskType.WAIT_FOR_WEBHOOK,
    required: ["matches"],
  },
  required: ["name", "taskReferenceName", "type", "inputParameters"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      anyOf: [
        {
          type: "object",
          properties: {
            matches: {
              anyOf: [
                {
                  type: "object",
                  additionalProperties: true,
                },
                {
                  type: "string",
                  pattern: variablePattern,
                },
              ],
            },
          },
        },
        {
          type: "string",
          pattern: variablePattern,
        },
      ],
    },
    type: { const: TaskType.WAIT_FOR_WEBHOOK },
  },
};

export const humanTaskSchema = {
  $id: "/properties/tasks/human",
  type: "object",
  description: "Will wait for human interaction",
  default: {
    name: "human_name",
    taskReferenceName: "human_ref",
    type: TaskType.HUMAN,
  },
  required: ["name", "taskReferenceName", "type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.HUMAN },
    inputParameters: {
      anyOf: [
        {
          type: "object",
          properties: {
            _humanTaskTemplate: {
              type: "string",
            },
            _humanTaskAssignmentPolicy: {
              anyOf: [
                {
                  type: "object",
                  properties: {
                    type: {
                      type: "string",
                    },
                    subjects: {
                      type: ["string", "array"],
                      items: {
                        type: "string",
                      },
                    },
                    groupId: {
                      type: "string",
                    },
                  },
                },
                {
                  type: "string",
                  pattern: variablePattern,
                },
              ],
            },
            _humanTaskTimeoutPolicy: {
              anyOf: [
                {
                  type: "object",
                  properties: {
                    type: {
                      type: "string",
                    },
                    timeoutSeconds: {
                      type: "integer",
                    },
                    subjects: {
                      type: ["string", "array"],
                      items: {
                        type: "string",
                      },
                    },
                  },
                },
                {
                  type: "string",
                  pattern: variablePattern,
                },
              ],
            },
            additionalProperties: true,
          },
        },
        {
          type: "string",
          pattern: variablePattern,
        },
      ],
    },
  },
  additionalProperties: true,
};

export const jdbcTaskSchema = {
  $id: "/properties/tasks/jdbcTaskSchema",
  type: "object",
  description: "JDBC task",
  default: {
    name: "jdbc_task_ref",
    taskReferenceName: "jdbc_task_ref",
    type: TaskType.JDBC,
    inputParameters: {
      connectionId: "", // TODO: will be deprecated
      integrationName: "",
      statement: "SELECT * FROM tableName WHERE id=?",
      parameters: [],
      expectedUpdateCount: 0,
      jdbcType: JDBCType.SELECT,
    },
  },
  required: ["name", "taskReferenceName", "inputParameters", "type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      type: ["object", "string"],
      properties: {
        connectionId: {
          type: "string",
        },
        integrationName: { type: "string" },
        statement: {
          type: "string",
        },
        parameters: {
          anyOf: [
            {
              type: "array",
            },
            {
              type: "string",
              pattern: variablePattern,
            },
          ],
        },
        expectedUpdateCount: {
          type: "string",
        },
        type: {
          type: "string",
          enum: Object.values(JDBCType),
        },
      },
      required: ["integrationName", "statement", "type"],
      additionalProperties: true,
    },
    type: { const: TaskType.JDBC },
  },
  additionalProperties: true,
};

export const updateSecretTaskSchema = {
  $id: "/properties/tasks/updateSecretTaskSchema",
  type: "object",
  description: "Update secret task",
  default: {
    name: "update_secret_task_ref",
    taskReferenceName: "update_secret_task_ref",
    type: TaskType.UPDATE_SECRET,
    inputParameters: {
      _secrets: {
        secretKey: "my_token",
        secretValue: "token value",
      },
    },
  },
  required: ["name", "taskReferenceName", "inputParameters", "type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      anyOf: [
        {
          type: "object",
          properties: {
            _secrets: {
              anyOf: [
                {
                  type: "object",
                  properties: {
                    secretKey: {
                      type: "string",
                    },
                    secretValue: {
                      type: "string",
                    },
                  },
                  required: ["secretKey", "secretValue"],
                },
                {
                  type: "string",
                  pattern: variablePattern,
                },
              ],
            },
          },
          required: ["_secrets"],
          additionalProperties: false,
        },
        {
          type: "string",
          pattern: variablePattern,
        },
      ],
    },
    type: { const: TaskType.UPDATE_SECRET },
  },
  additionalProperties: true,
};

export const queryProcessorTaskSchema = {
  $id: "/properties/tasks/queryProcessorTaskSchema",
  type: "object",
  description: "Query processor task",
  default: {
    name: "query_processor_task_ref",
    taskReferenceName: "query_processor_task_ref",
    type: TaskType.QUERY_PROCESSOR,
    inputParameters: {
      workflowNames: [],
      statuses: [],
      queryType: QueryProcessorType.CONDUCTOR_API,
    },
  },
  required: ["name", "taskReferenceName", "inputParameters", "type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      type: ["object", "string"],
      properties: {
        workflowNames: {
          anyOf: [
            {
              type: "array",
            },
            {
              type: "string",
              pattern: variablePattern,
            },
          ],
        },
        statuses: {
          anyOf: [
            {
              type: "array",
            },
            {
              type: "string",
              pattern: variablePattern,
            },
          ],
        },
        queryType: {
          type: "string",
          enum: Object.values(QueryProcessorType),
        },
      },
      required: ["queryType"],
      additionalProperties: true,
    },
    type: { const: TaskType.QUERY_PROCESSOR },
  },
  additionalProperties: true,
};

export const getSignedJwtTaskSchema = {
  $id: "/properties/tasks/getSignedJwtTaskSchema",
  type: "object",
  description: "Get signed JWT task",
  default: {
    name: "get_signed_jwt_task_ref",
    taskReferenceName: "get_signed_jwt_task_ref",
    type: TaskType.GET_SIGNED_JWT,
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
  },
  required: ["name", "taskReferenceName", "inputParameters", "type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      type: ["object", "string"],
      properties: {
        subject: {
          type: "string",
        },
        issuer: {
          type: "string",
        },
        privateKey: {
          type: "string",
        },
        privateKeyId: {
          type: "string",
        },
        audience: {
          type: "string",
        },
        ttlInSecond: {
          anyOf: [
            {
              type: "number",
            },
            {
              type: "string",
              pattern: ".*\\$\\{.*",
            },
          ],
        },
        scopes: {
          anyOf: [
            {
              type: "array",
              items: {
                type: "string",
              },
            },
            {
              type: "string",
              pattern: ".*\\$\\{.*",
            },
          ],
        },
        algorithm: {
          type: "string",
          enum: Object.values(GetSignedJWTAlgorithmType),
        },
      },
      required: [],
      additionalProperties: true,
    },
    type: { const: TaskType.GET_SIGNED_JWT },
  },
  additionalProperties: true,
};

export const opsGenieTaskSchema = {
  $id: "/properties/tasks/opsGenieTaskSchema",
  type: "object",
  description: "Ops genie task",
  default: {
    name: "ops_genie_task_ref",
    taskReferenceName: "ops_genie_task_ref",
    type: TaskType.OPS_GENIE,
    inputParameters: {},
  },
  required: ["name", "taskReferenceName", "inputParameters", "type"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      type: ["object", "string"],
      properties: {
        alias: {
          type: "string",
        },
        description: {
          type: "string",
        },
        message: {
          type: "string",
        },
      },
      required: [],
      additionalProperties: true,
    },
    type: { const: TaskType.OPS_GENIE },
  },
  additionalProperties: true,
};

export const updateTaskSchema = {
  $id: "/properties/tasks/updateTask",
  type: "object",
  description: "Update task",
  default: {
    name: "update_task_ref",
    taskReferenceName: "update_task_ref",
    type: TaskType.UPDATE_TASK,
    inputParameters: {},
  },
  required: [],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      type: ["object", "string"],
      properties: {
        taskStatus: {
          anyOf: [
            {
              type: "string",
              enum: Object.values(UpdateTaskStatus),
            },
            {
              type: "string",
              pattern: variablePattern,
            },
          ],
        },
        taskRefName: {
          type: "string",
        },
        workflowId: {
          type: "string",
        },
        taskId: {
          type: "string",
        },
        taskOutput: {
          type: "object",
        },
        mergeOutput: {
          type: "boolean",
        },
      },
      additionalProperties: true,
    },
    type: { const: TaskType.UPDATE_TASK },
  },
  additionalProperties: true,
};

export const getWorkflowSchema = {
  $id: "/properties/tasks/getWorkflowSchema",
  type: "object",
  description: "Get Workflow",
  default: {
    name: "get_workflow_task_ref",
    taskReferenceName: "get_workflow_task_ref",
    type: TaskType.UPDATE_TASK,
  },
  required: ["name", "taskReferenceName", "type", "inputParameters"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.GET_WORKFLOW },
    workflowId: {
      type: "string",
    },
    includeTasks: {
      type: "boolean",
    },
  },
  additionalProperties: true,
};

export const chunkTextTaskSchema = {
  $id: "/properties/tasks/chunkTextTaskSchema",
  type: "object",
  description: "Chunk text task",
  default: {
    name: "chunk_text_task_ref",
    taskReferenceName: "chunk_text_task_ref",
    type: TaskType.CHUNK_TEXT,
  },
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    inputParameters: {
      type: ["object", "string"],
      properties: {
        text: {
          type: "string",
        },
        chunkSize: {
          type: "number",
        },
        mediaType: {
          type: "string",
        },
      },
      required: [],
      additionalProperties: true,
    },
    type: { const: TaskType.CHUNK_TEXT },
  },
};

export const listFilesTaskSchema = {
  $id: "/properties/tasks/listFilesTaskSchema",
  type: "object",
  description: "List Files task",
  default: {
    name: "list_files_task_ref",
    taskReferenceName: "list_files_task_ref",
    type: TaskType.LIST_FILES,
  },
  required: ["name", "taskReferenceName", "type", "inputParameters"],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.LIST_FILES },
    inputParameters: {
      type: "object",
      required: ["inputLocation"],
      properties: {
        inputLocation: {
          type: "string",
        },
        integrationName: {
          type: "string",
        },
        outputLocation: {
          type: "string",
        },
        fileTypes: {
          type: "array",
          items: {
            type: "string",
          },
        },
        integrationNames: {
          type: "object",
          additionalProperties: {
            type: "string",
          },
        },
      },
      additionalProperties: true,
    },
  },
};

export const parseDocumentTaskSchema = {
  $id: "/properties/tasks/parseDocumentTaskSchema",
  type: "object",
  description: "Parse document",
  default: {
    name: "parse_document_task_ref",
    taskReferenceName: "parse_document_task_ref",
    type: TaskType.PARSE_DOCUMENT,
  },
  required: [],
  properties: {
    name: { $ref: nameSchema.$id },
    taskReferenceName: { $ref: taskReferenceName.$id },
    type: { const: TaskType.PARSE_DOCUMENT },
    integrationName: {
      type: "string",
    },
    url: {
      type: "string",
    },
    mediaType: {
      type: "string",
    },
    chunkSize: {
      type: "number",
    },
  },
  additionalProperties: true,
};

export const tasksItemsSchema = {
  $id: "/properties/tasks",
  type: "array",
  title: "Workflow Tasks",
  description: "This list holds the tasks for your workflow.",
  default: [],
  items: {
    $id: "/properties/tasks/items",
    oneOf: [
      { $ref: simpleTaskSchema.$id },
      { $ref: yieldTaskSchema.$id },
      { $ref: doWhileSchema.$id },
      { $ref: forkTaskSchema.$id },
      { $ref: joinTaskSchema.$id },
      { $ref: waitSchema.$id },
      { $ref: forkJoinDynamicSchema.$id },
      { $ref: dynamicTaskSchema.$id },
      { $ref: terminateTaskSchema.$id },
      { $ref: setVariableTaskSchema.$id },
      { $ref: subWorkflowTaskSchema.$id },
      { $ref: jsonJQTaskSchema.$id },
      { $ref: httpTaskSchema.$id },
      { $ref: switchTaskSchema.$id },
      { $ref: inlineTaskSchema.$id },
      { $ref: eventTaskSchema.$id },
      { $ref: kafkaRequestTaskSchema.$id },
      { $ref: terminateWorkflowSchema.$id },
      { $ref: genericSchema.$id },
      { $ref: businessRuleSchema.$id },
      { $ref: sendgridSchema.$id },
      { $ref: humanTaskSchema.$id },
      { $ref: startWorkflowTaskSchema.$id },
      { $ref: httpPollTaskSchema.$id },
      { $ref: webhookTaskSchema.$id },
      { $ref: jdbcTaskSchema.$id },
      { $ref: updateSecretTaskSchema.$id },
      { $ref: queryProcessorTaskSchema.$id },
      { $ref: opsGenieTaskSchema.$id },
      { $ref: getSignedJwtTaskSchema.$id },
      { $ref: updateTaskSchema.$id },
      { $ref: getWorkflowSchema.$id },
      { $ref: chunkTextTaskSchema.$id },
      { $ref: listFilesTaskSchema.$id },
      { $ref: parseDocumentTaskSchema.$id },
    ],
  },
};

export const schemasByType = {
  [TaskType.SIMPLE]: simpleTaskSchema,
  [TaskType.YIELD]: yieldTaskSchema,
  [TaskType.DO_WHILE]: doWhileSchema,
  [TaskType.FORK_JOIN]: forkTaskSchema,
  [TaskType.WAIT]: waitSchema,
  [TaskType.FORK_JOIN_DYNAMIC]: forkJoinDynamicSchema,
  [TaskType.DYNAMIC]: dynamicTaskSchema,
  [TaskType.TERMINATE]: terminateTaskSchema,
  [TaskType.SET_VARIABLE]: setVariableTaskSchema,
  [TaskType.SUB_WORKFLOW]: subWorkflowTaskSchema,
  [TaskType.JSON_JQ_TRANSFORM]: jsonJQTaskSchema,
  [TaskType.HTTP]: httpTaskSchema,
  [TaskType.SWITCH]: switchTaskSchema,
  [TaskType.INLINE]: inlineTaskSchema,
  [TaskType.JOIN]: joinTaskSchema,
  [TaskType.EVENT]: eventTaskSchema,
  [TaskType.KAFKA_PUBLISH]: kafkaRequestTaskSchema,
  [TaskType.TERMINATE_WORKFLOW]: terminateWorkflowSchema,
  [TaskType.BUSINESS_RULE]: businessRuleSchema,
  [TaskType.SENDGRID]: sendgridSchema,
  [TaskType.START]: genericSchema,
  [TaskType.DECISION]: genericSchema,
  [TaskType.USER_DEFINED]: genericSchema,
  [TaskType.LAMBDA]: genericSchema,
  [TaskType.EXCLUSIVE_JOIN]: genericSchema,
  [TaskType.TERMINAL]: genericSchema,
  [TaskType.HUMAN]: humanTaskSchema,
  [TaskType.TASK_SUMMARY]: genericSchema,
  [TaskType.WAIT_FOR_EVENT]: genericSchema,
  [TaskType.WAIT_FOR_WEBHOOK]: webhookTaskSchema,
  [TaskType.START_WORKFLOW]: startWorkflowTaskSchema,
  [TaskType.HTTP_POLL]: httpPollTaskSchema,
  [TaskType.JDBC]: jdbcTaskSchema,
  [TaskType.IA_TASK]: genericSchema,
  [TaskType.SWITCH_JOIN]: genericSchema, // Pseudo task does not really exist
  [TaskType.LLM_TEXT_COMPLETE]: genericSchema,
  [TaskType.LLM_GENERATE_EMBEDDINGS]: genericSchema,
  [TaskType.LLM_GET_EMBEDDINGS]: genericSchema,
  [TaskType.LLM_STORE_EMBEDDINGS]: genericSchema,
  [TaskType.LLM_SEARCH_INDEX]: genericSchema,
  [TaskType.LLM_INDEX_DOCUMENT]: genericSchema,
  [TaskType.GET_DOCUMENT]: genericSchema,
  [TaskType.LLM_CHAT_COMPLETE]: genericSchema,
  [TaskType.LLM_INDEX_TEXT]: genericSchema,
  [TaskType.UPDATE_SECRET]: updateSecretTaskSchema,
  [TaskType.JUMP]: genericSchema,
  [TaskType.QUERY_PROCESSOR]: queryProcessorTaskSchema,
  [TaskType.OPS_GENIE]: opsGenieTaskSchema,
  [TaskType.GET_SIGNED_JWT]: getSignedJwtTaskSchema,
  [TaskType.UPDATE_TASK]: updateTaskSchema,
  [TaskType.GET_WORKFLOW]: getWorkflowSchema,
  [TaskType.GRPC]: genericSchema,
  [TaskType.MCP]: genericSchema,
  [TaskType.MCP_REMOTE]: genericSchema,
  [TaskType.CHUNK_TEXT]: chunkTextTaskSchema,
  [TaskType.LIST_FILES]: listFilesTaskSchema,
  [TaskType.PARSE_DOCUMENT]: parseDocumentTaskSchema,
};

// Object.values(TaskType)
export const workflowSchema = {
  $id: "/workflow-schema",
  required: ["name", "version", "tasks", "schemaVersion"],
  type: "object",
  properties: {
    name: {
      $id: "/properties/name",
      default: "",
      description: WORKFLOW_NAME_ERROR_MESSAGE,
      maxLength: 100,
      pattern: regexToString(WORKFLOW_NAME_REGEX),
      title: "Workflow Name",
      type: "string",
    },
    description: {
      $id: "/properties/description",
      type: "string",
      title: "Workflow Description",
      description: "An brief description of your workflow for reference.",
      default: "",
    },
    version: {
      $id: "/properties/version",
      default: 0,
      description: "An explanation about the purpose of this instance.",
      title: "The version schema",
      minimum: 1,
      type: "integer",
    },
    tasks: {
      $ref: tasksItemsSchema.$id,
    },
    inputParameters: {
      $id: "/properties/inputParameters",
      type: "array",
      title: "Workflow Input Parameters",
      description: "An explanation about the purpose of this instance.",
      default: [],
      examples: [[]],
      items: {
        $id: "/properties/inputParameters/items",
      },
    },
    outputParameters: {
      $id: "/properties/outputParameters",
      type: "object",
      title: "The outputParameters schema",
      description: "An explanation about the purpose of this instance.",
      default: {},
      required: [],
      properties: {},
      additionalProperties: true,
    },
    schemaVersion: {
      $id: "/properties/schemaVersion",
      type: "integer",
      title: "Schema Version",
      description: "Fixed schema version",
      default: 2,
    },
    restartable: {
      $id: "/properties/restartable",
      type: "boolean",
      title: "Workflow restartable",
      description: "Specify if the workflow is restartable.",
      default: true,
    },
    workflowStatusListenerEnabled: {
      $id: "/properties/workflowStatusListenerEnabled",
      type: "boolean",
      title: "The workflowStatusListenerEnabled schema",
      description: "An explanation about the purpose of this instance.",
      default: false,
    },
    ownerEmail: {
      $id: "/properties/ownerEmail",
      type: "string",
      title: "The ownerEmail schema",
      description: "An explanation about the purpose of this instance.",
      default: "",
    },
    timeoutPolicy: {
      $id: "/properties/timeoutPolicy",
      type: "string",
      enum: Object.values(TimeoutPolicy),
      title: "The timeoutPolicy schema",
      description: "An explanation about the purpose of this instance.",
      default: "",
    },
    timeoutSeconds: {
      $id: "/properties/timeoutSeconds",
      type: "integer",
      title: "The timeoutSeconds schema",
      description: "An explanation about the purpose of this instance.",
      default: 0,
    },
    failureWorkflow: {
      $id: "/properties/failureWorkflow",
      type: "string",
      title: "Failure Workflow Name",
      description: "Failure Workflow",
      default: "",
    },
  },
  additionalProperties: true,
};

export const workflowDefinitionSchemaWithDeps = [
  // Note that order matters here.
  nameSchema,
  taskReferenceName,
  inputParameters,
  simpleTaskSchema,
  yieldTaskSchema,
  doWhileSchema,
  joinTaskSchema,
  forkTaskSchema,
  waitSchema,
  forkJoinDynamicSchema,
  dynamicTaskSchema,
  terminateTaskSchema,
  setVariableTaskSchema,
  humanTaskSchema,
  webhookTaskSchema,
  subWorkflowTaskSchema,
  terminateWorkflowSchema,
  genericSchema,
  jsonJQTaskSchema,
  eventTaskSchema,
  kafkaRequestTaskSchema,
  businessRuleSchema,
  sendgridMailRequestSchema,
  sendgridSchema,
  switchTaskSchema,
  inlineTaskSchema,
  baseHTTPRequestSchema,
  httpTaskSchema,
  httpPollTaskSchema,
  startWorkflowTaskSchema,
  tasksItemsSchema,
  jdbcTaskSchema,
  updateSecretTaskSchema,
  queryProcessorTaskSchema,
  opsGenieTaskSchema,
  getSignedJwtTaskSchema,
  updateTaskSchema,
  getWorkflowSchema,
  chunkTextTaskSchema,
  listFilesTaskSchema,
  parseDocumentTaskSchema,
  // workflow must be at the end, because it wraps another schemas
  workflowSchema,
];
