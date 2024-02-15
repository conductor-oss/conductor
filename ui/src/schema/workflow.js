/* eslint-disable no-template-curly-in-string */

export const NEW_WORKFLOW_TEMPLATE = {
  name: "",
  description:
    "Edit or extend this sample workflow. Set the workflow name to get started",
  version: 1,
  tasks: [
    {
      name: "call_remote_api",
      taskReferenceName: "call_remote_api",
      inputParameters: {
        http_request: {
          uri: "https://orkes-api-tester.orkesconductor.com/api",
          method: "GET",
        },
      },
      type: "HTTP",
    },
  ],
  inputParameters: [],
  outputParameters: {
    data: "${call_remote_api.output.response.body.data}",
    source: "${call_remote_api.output.response.body.source}",
  },
  schemaVersion: 2,
  restartable: true,
  workflowStatusListenerEnabled: false,
  ownerEmail: "example@email.com",
  timeoutPolicy: "ALERT_ONLY",
  timeoutSeconds: 0,
};

const WORKFLOW_SCHEMA = {
  $schema: "http://json-schema.org/draft-07/schema",
  $id: "http://example.com/example.json",
  type: "object",
  title: "The root schema",
  description: "The root schema comprises the entire JSON document.",
  default: {},
  examples: [
    {
      name: "first_sample_workflow",
      description: "First Sample Workflow",
      version: 1,
      tasks: [
        {
          name: "call_remote_api",
          taskReferenceName: "call_remote_api",
          inputParameters: {
            http_request: {
              uri: "https://orkes-api-tester.orkesconductor.com/api",
              method: "GET",
            },
          },
          type: "HTTP",
        },
      ],
      inputParameters: [],
      outputParameters: {
        data: "${call_remote_api.output.response.body.data}",
        source: "${call_remote_api.output.response.body.source}",
      },
      schemaVersion: 2,
      restartable: true,
      workflowStatusListenerEnabled: false,
      ownerEmail: "example@email.com",
      timeoutPolicy: "ALERT_ONLY",
      timeoutSeconds: 0,
    },
  ],
  required: ["name", "version", "tasks", "schemaVersion"],
  properties: {
    name: {
      $id: "#/properties/name",
      default: "",
      description:
        "Workflow Name - should be without spaces or special characters. Underscores and periods are allowed.",
      examples: ["first_sample_workflow"],
      maxLength: 100,
      pattern: "^[\\w\\.]+$",
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
            name: "call_remote_api",
            taskReferenceName: "call_remote_api",
            inputParameters: {
              http_request: {
                uri: "https://orkes-api-tester.orkesconductor.com/api",
                method: "GET",
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
                name: "call_remote_api",
                taskReferenceName: "call_remote_api",
                inputParameters: {
                  http_request: {
                    uri: "https://orkes-api-tester.orkesconductor.com/api",
                    method: "GET",
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
                examples: ["call_remote_api"],
              },
              taskReferenceName: {
                $id: "#/properties/tasks/items/anyOf/0/properties/taskReferenceName",
                type: "string",
                title: "Task Reference Name",
                description:
                  "A unique task reference name for this task in the entire workflow",
                default: "",
                examples: ["call_remote_api"],
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
                      uri: "https://orkes-api-tester.orkesconductor.com/api",
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
          data: "${call_remote_api.output.response.body.data}",
          source: "${call_remote_api.output.response.body.source}",
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
  },
  additionalProperties: true,
};

export const JSON_FILE_NAME = "file:///workflow.json";

export function configureMonaco(monaco) {
  monaco.languages.typescript.javascriptDefaults.setEagerModelSync(true);
  // noinspection JSUnresolvedVariable
  monaco.languages.typescript.javascriptDefaults.setCompilerOptions({
    target: monaco.languages.typescript.ScriptTarget.ES6,
    allowNonTsExtensions: true,
  });
  let modelUri = monaco.Uri.parse(JSON_FILE_NAME);
  monaco.languages.json.jsonDefaults.setDiagnosticsOptions({
    validate: true,
    schemas: [
      {
        uri: "http://conductor.tmp/schemas/workflow.json", // id of the first schema
        fileMatch: [modelUri.toString()], // associate with our model
        schema: WORKFLOW_SCHEMA,
      },
    ],
  });
}
