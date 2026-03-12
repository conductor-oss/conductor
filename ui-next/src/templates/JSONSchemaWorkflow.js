import { JSON_SCHEMA_DRAFT_07_URL } from "utils/constants/jsonSchema";

export const NEW_TASK_TEMPLATE = {
  name: "",
  description: "",
  retryCount: 3,
  timeoutSeconds: 3600,
  timeoutPolicy: "TIME_OUT_WF",
  retryLogic: "FIXED",
  retryDelaySeconds: 60,
  responseTimeoutSeconds: 600,
  rateLimitPerFrequency: 0,
  rateLimitFrequencyInSeconds: 1,
  ownerEmail: "example@email.com",
  pollTimeoutSeconds: 3600,
  inputKeys: [],
  outputKeys: [],
  inputTemplate: {},
  backoffScaleFactor: 1,
  concurrentExecLimit: 0,
};

export const newTaskTemplate = (ownerEmail) => {
  return { ...NEW_TASK_TEMPLATE, ownerEmail };
};

export const NEW_WORKFLOW_TEMPLATE = {
  name: "",
  description: "",
  version: 1,
  tasks: [],
  inputParameters: [],
  outputParameters: {},
  schemaVersion: 2,
  restartable: true,
  workflowStatusListenerEnabled: false,
  ownerEmail: "example@email.com",
  timeoutPolicy: "ALERT_ONLY",
  timeoutSeconds: 0,
  failureWorkflow: "",
};

export const newWorkflowTemplate = (ownerEmail) => {
  // generate random string of six characters
  const suffix = Math.random().toString(36).substring(2, 7);

  return {
    ...NEW_WORKFLOW_TEMPLATE,
    name: `NewWorkflow_${suffix}`,
    ownerEmail,
  };
};

export const WORKFLOW_SCHEMA = {
  $schema: JSON_SCHEMA_DRAFT_07_URL,
  $id: "http://example.com/example.json",
  type: "object",
  title: "The root schema",
  description: "The root schema comprises the entire JSON document.",
  default: {},
  examples: [
    {
      name: "first_sample_workflow",
      description: "First Sample Workflow by Orkes",
      version: 1,
      tasks: [
        {
          name: "get_random_fact",
          taskReferenceName: "get_random_fact_ref",
          inputParameters: {
            http_request: {
              uri: "https://catfact.ninja/fact",
              method: "GET",
              connectionTimeOut: 3000,
              readTimeOut: 3000,
            },
          },
          type: "HTTP",
        },
      ],
      inputParameters: [],
      outputParameters: {
        data: "${get_random_fact_ref.output.response.body}",
      },
      schemaVersion: 2,
      restartable: true,
      workflowStatusListenerEnabled: false,
      ownerEmail: "example@email.com",
      timeoutPolicy: "ALERT_ONLY",
      timeoutSeconds: 0,
      failureWorkflow: "",
    },
  ],
  required: ["name", "description", "version", "tasks", "schemaVersion"],
  properties: {
    name: {
      $id: "#/properties/name",
      default: "",
      description:
        "Workflow Name - should be without spaces or special characters. Underscores are allowed.",
      examples: ["first_sample_workflow"],
      maxLength: 100,
      pattern: "(^\\w+$)|(^\\w[\\w|-]+\\w$)",
      title: "Workflow Name",
      type: "string",
    },
    description: {
      $id: "#/properties/description",
      type: "string",
      title: "Workflow Description",
      description: "An brief description of your workflow for reference.",
      default: "",
      examples: ["First Sample Workflow"],
    },
    version: {
      $id: "#/properties/version",
      default: 0,
      description: "An explanation about the purpose of this instance.",
      examples: [1],
      title: "The version schema",
      minimum: 1,
      type: "integer",
    },
    tasks: {
      $id: "#/properties/tasks",
      type: "array",
      title: "Workflow Tasks",
      description: "This list holds the tasks for your workflow.",
      default: [],
      examples: [
        [
          {
            name: "get_random_fact",
            taskReferenceName: "get_random_fact_ref",
            inputParameters: {
              http_request: {
                uri: "https://catfact.ninja/fact",
                method: "GET",
                connectionTimeOut: 3000,
                readTimeOut: 3000,
              },
            },
            type: "HTTP",
          },
        ],
      ],
      additionalItems: true,
      items: {
        $id: "#/properties/tasks/items",
        anyOf: [
          {
            $id: "#/properties/tasks/items/anyOf/0",
            type: "object",
            title: "The first anyOf schema",
            description: "Workflow task details",
            default: {
              name: "",
              taskReferenceName: "",
              inputParameters: {},
              type: "SIMPLE",
            },
            examples: [
              {
                name: "get_random_fact",
                taskReferenceName: "get_random_fact_ref",
                inputParameters: {
                  http_request: {
                    uri: "https://catfact.ninja/fact",
                    method: "GET",
                    connectionTimeOut: 3000,
                    readTimeOut: 3000,
                  },
                },
                type: "HTTP",
              },
            ],
            required: ["name", "taskReferenceName", "inputParameters", "type"],
            properties: {
              name: {
                $id: "#/properties/tasks/items/anyOf/0/properties/name",
                type: "string",
                title: "Task name",
                description: "Task name",
                default: "",
                examples: ["get_population_data"],
              },
              taskReferenceName: {
                $id: "#/properties/tasks/items/anyOf/0/properties/taskReferenceName",
                type: "string",
                title: "Task Reference Name",
                description:
                  "A unique task reference name for this task in the entire workflow",
                default: "",
                examples: ["get_population_data"],
              },
              inputParameters: {
                $id: "#/properties/tasks/items/anyOf/0/properties/inputParameters",
                type: "object",
                title: "Input Parameters",
                description: "Task input parameters",
                default: {},
                examples: [
                  {
                    http_request: {
                      uri: "https://datausa.io/api/data?drilldowns=Nation&measures=Population",
                      method: "GET",
                    },
                  },
                ],
                required: [],
                properties: {},
                additionalProperties: true,
              },
              type: {
                $id: "#/properties/tasks/items/anyOf/0/properties/type",
                type: "string",
                title: "Task Type",
                description: "Task type",
                default: "",
                examples: ["HTTP"],
              },
            },
            additionalProperties: true,
          },
        ],
      },
    },
    inputParameters: {
      $id: "#/properties/inputParameters",
      type: "array",
      title: "Workflow Input Parameters",
      description: "An explanation about the purpose of this instance.",
      default: [],
      examples: [[]],
      additionalItems: true,
      items: {
        $id: "#/properties/inputParameters/items",
      },
    },
    outputParameters: {
      $id: "#/properties/outputParameters",
      type: "object",
      title: "The outputParameters schema",
      description: "An explanation about the purpose of this instance.",
      default: {},
      examples: [
        {
          data: "${task_ref.output.dataVariable}",
          source: "${task_ref.output.sourceVariable}",
        },
      ],
      required: [],
      properties: {},
      additionalProperties: true,
    },
    schemaVersion: {
      $id: "#/properties/schemaVersion",
      type: "integer",
      title: "Schema Version",
      description: "Fixed schema version",
      default: 2,
      examples: [2],
    },
    restartable: {
      $id: "#/properties/restartable",
      type: "boolean",
      title: "Workflow restartable",
      description: "Specify if the workflow is restartable.",
      default: true,
      examples: [true, false],
    },
    workflowStatusListenerEnabled: {
      $id: "#/properties/workflowStatusListenerEnabled",
      type: "boolean",
      title: "The workflowStatusListenerEnabled schema",
      description: "An explanation about the purpose of this instance.",
      default: false,
      examples: [true, false],
    },
    ownerEmail: {
      $id: "#/properties/ownerEmail",
      type: "string",
      title: "The ownerEmail schema",
      description: "An explanation about the purpose of this instance.",
      default: "",
      examples: ["example@email.com"],
    },
    timeoutPolicy: {
      $id: "#/properties/timeoutPolicy",
      type: "string",
      title: "The timeoutPolicy schema",
      description: "An explanation about the purpose of this instance.",
      default: "",
      examples: ["ALERT_ONLY", "TIME_OUT_WF"],
    },
    timeoutSeconds: {
      $id: "#/properties/timeoutSeconds",
      type: "integer",
      title: "The timeoutSeconds schema",
      description: "An explanation about the purpose of this instance.",
      default: 0,
      examples: [0],
    },
    failureWorkflow: {
      $id: "#/properties/failureWorkflow",
      type: "string",
      title: "Failue Workflow Name",
      description: "Specify the Failure Workflow Name.",
      default: "",
      examples: ["shipping_failure"],
    },
  },
  additionalProperties: true,
};
