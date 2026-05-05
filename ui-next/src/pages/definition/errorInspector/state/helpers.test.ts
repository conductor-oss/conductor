import { NodeTaskData } from "components/features/flow/nodes/mapper/types";
import { NodeData } from "reaflow";
import { TaskDef, TaskType } from "types";
import { CrumbMap } from "types/Crumbs";
import {
  NodeInnerData,
  filterServerErrorsNotPresentInNodes,
  getVariablesForEachTasks,
  jakatraPathToPropertyPath,
  nodesToCrumbMap,
  reverifyServerErrorsTaskChanges,
  serverValidationErrorToIndexTask,
} from "./helpers";
import { ErrorIds, ErrorSeverity, ErrorTypes } from "./types";

export const simpleNodeDiagram = [
  {
    id: "start",
    text: "start",
    ports: [
      {
        id: "start-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
      },
    ],
    data: {
      task: {
        name: "start",
        taskReferenceName: "start",
        type: "TERMINAL",
      },
      crumbs: [],
      selected: false,
    },
    width: 80,
    height: 80,
  },
  {
    id: "get_random_fact",
    text: "get_random_fact",
    ports: [
      {
        id: "get_random_fact-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
      },
    ],
    data: {
      task: {
        name: "get_random_fact",
        taskReferenceName: "get_random_fact",
        inputParameters: {
          http_request: {
            uri: "https://catfact.ninja/fact",
            method: "GET",
            connectionTimeOut: 3000,
            readTimeOut: 3000,
          },
        },
        type: "HTTP",
        decisionCases: {},
        defaultCase: [],
        forkTasks: [],
        startDelay: 0,
        joinOn: [],
        optional: false,
        defaultExclusiveJoinTask: [],
        asyncComplete: false,
        loopOver: [],
      },
      crumbs: [
        {
          parent: null,
          ref: "get_random_fact",
          refIdx: 0,
        },
      ],
      selected: false,
    },
    width: 350,
    height: 130,
  },
  {
    id: "http_lvdn9_ref",
    text: "http_lvdn9_ref",
    ports: [
      {
        id: "http_lvdn9_ref-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
      },
    ],
    data: {
      task: {
        name: "http_lvdn9_ref",
        taskReferenceName: "http_lvdn9_ref",
        type: "HTTP",
        inputParameters: {
          http_request: {
            uri: "https://orkes-api-tester.orkesconductor.com/get",
            method: "GET",
            connectionTimeOut: 3000,
            readTimeOut: 3000,
          },
        },
      },
      crumbs: [
        {
          parent: null,
          ref: "get_random_fact",
          refIdx: 0,
        },
        {
          parent: null,
          ref: "http_lvdn9_ref",
          refIdx: 1,
        },
      ],
      selected: true,
    },
    width: 350,
    height: 130,
  },
  {
    id: "end",
    text: "end",
    data: {
      task: {
        name: "end",
        taskReferenceName: "end",
        type: "TERMINAL",
      },
      crumbs: [],
      selected: false,
    },
    width: 80,
    height: 80,
  },
];
const withExpandedSubWorkflow = [
  {
    id: "start",
    text: "start",
    ports: [
      {
        id: "start-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
      },
    ],
    data: {
      task: {
        name: "start",
        taskReferenceName: "start",
        type: "TERMINAL",
      },
      crumbs: [],
      selected: false,
    },
    width: 80,
    height: 80,
  },
  {
    id: "get_random_fact",
    text: "get_random_fact",
    ports: [
      {
        id: "get_random_fact-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
      },
    ],
    data: {
      task: {
        name: "get_random_fact",
        taskReferenceName: "get_random_fact",
        inputParameters: {
          http_request: {
            uri: "https://catfact.ninja/fact",
            method: "GET",
            connectionTimeOut: 3000,
            readTimeOut: 3000,
          },
        },
        type: "HTTP",
        decisionCases: {},
        defaultCase: [],
        forkTasks: [],
        startDelay: 0,
        joinOn: [],
        optional: false,
        defaultExclusiveJoinTask: [],
        asyncComplete: false,
        loopOver: [],
      },
      crumbs: [
        {
          parent: null,
          ref: "get_random_fact",
          refIdx: 0,
        },
      ],
      selected: false,
    },
    width: 350,
    height: 130,
  },
  {
    id: "sub_workflow_u58mg_ref",
    text: "sub_workflow_u58mg_ref",
    ports: [
      {
        id: "sub_workflow_u58mg_ref-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
      },
    ],
    data: {
      task: {
        name: "sub_workflow_u58mg_ref",
        taskReferenceName: "sub_workflow_u58mg_ref",
        inputParameters: {},
        type: "SUB_WORKFLOW",
        subWorkflowParam: {
          name: "image_convert_resize",
          version: 1,
        },
      },
      crumbs: [
        {
          parent: null,
          ref: "get_random_fact",
          refIdx: 0,
        },
        {
          parent: null,
          ref: "sub_workflow_u58mg_ref",
          refIdx: 1,
        },
      ],
      selected: true,
    },
    width: 350,
    height: 100,
  },
  {
    text: "image_convert_resize",
    data: {
      task: {
        name: "image_convert_resize",
        taskReferenceName: "image_convert_resize_ref",
        inputParameters: {
          fileLocation: "${workflow.input.fileLocation}",
          outputFormat: "${workflow.input.recipeParameters.outputFormat}",
          outputWidth: "${workflow.input.recipeParameters.outputSize.width}",
          outputHeight: "${workflow.input.recipeParameters.outputSize.height}",
        },
        type: "SIMPLE",
        decisionCases: {},
        defaultCase: [],
        forkTasks: [],
        startDelay: 0,
        joinOn: [],
        optional: false,
        defaultExclusiveJoinTask: [],
        asyncComplete: false,
        loopOver: [],
      },
      crumbs: [
        {
          parent: null,
          ref: "get_random_fact",
          refIdx: 0,
        },
        {
          parent: null,
          ref: "sub_workflow_u58mg_ref",
          refIdx: 1,
        },
        {
          parent: "sub_workflow_u58mg_ref",
          ref: "image_convert_resize_ref",
          refIdx: 0,
        },
      ],
      withinExpandedSubWorkflow: true,
      selected: false,
    },
    width: 350,
    height: 100,
    ports: [
      {
        id: "image_convert_resize_ref-south-port_swt_image_convert_resize_zwn03",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
        hidden: true,
      },
    ],
    id: "image_convert_resize_ref_swt_image_convert_resize_zwn03",
    parent: "sub_workflow_u58mg_ref",
  },
  {
    text: "upload_toS3",
    data: {
      task: {
        name: "upload_toS3",
        taskReferenceName: "upload_toS3_ref",
        inputParameters: {
          fileLocation: "${image_convert_resize_ref.output.fileLocation}",
        },
        type: "SIMPLE",
        decisionCases: {},
        defaultCase: [],
        forkTasks: [],
        startDelay: 0,
        joinOn: [],
        optional: false,
        defaultExclusiveJoinTask: [],
        asyncComplete: false,
        loopOver: [],
      },
      crumbs: [
        {
          parent: null,
          ref: "get_random_fact",
          refIdx: 0,
        },
        {
          parent: null,
          ref: "sub_workflow_u58mg_ref",
          refIdx: 1,
        },
        {
          parent: "sub_workflow_u58mg_ref",
          ref: "image_convert_resize_ref",
          refIdx: 0,
        },
        {
          parent: "sub_workflow_u58mg_ref",
          ref: "upload_toS3_ref",
          refIdx: 1,
        },
      ],
      withinExpandedSubWorkflow: true,
      selected: false,
    },
    width: 350,
    height: 100,
    ports: [
      {
        id: "upload_toS3_ref-south-port_swt_image_convert_resize_zwn03",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
        hidden: true,
      },
    ],
    id: "upload_toS3_ref_swt_image_convert_resize_zwn03",
    parent: "sub_workflow_u58mg_ref",
  },
  {
    id: "end",
    text: "end",
    data: {
      task: {
        name: "end",
        taskReferenceName: "end",
        type: "TERMINAL",
      },
      crumbs: [],
      selected: false,
    },
    width: 80,
    height: 80,
  },
];

describe("nodesToCrumbMap", () => {
  it("Should return every existing taskReference in diagram", () => {
    const result = nodesToCrumbMap(
      simpleNodeDiagram as unknown as NodeData<NodeInnerData>[],
    );
    expect(Object.keys(result)).toEqual(["get_random_fact", "http_lvdn9_ref"]);
  });
  it("Should not include subworkflow child ids if expanded subworkflow", () => {
    const result = nodesToCrumbMap(
      withExpandedSubWorkflow as unknown as NodeData<NodeInnerData>[],
    );
    expect(Object.keys(result)).toEqual([
      "get_random_fact",
      "sub_workflow_u58mg_ref",
    ]);
  });
});

const crumbMaps = {
  set_variable_ref: {
    task: {
      name: "set_variable",
      taskReferenceName: "set_variable_ref",
      type: "SET_VARIABLE",
      inputParameters: {
        name: "Orkes",
      },
    },
    crumbs: [
      {
        parent: null,
        ref: "set_variable_ref",
        refIdx: 0,
        type: "SET_VARIABLE",
      },
    ],
  },
  simple_ref: {
    task: {
      name: "simple",
      taskReferenceName: "simple_ref",
      type: "SIMPLE",
      inputParameters: {
        "Some-key-kf5rz": "${workflow.variables}",
      },
    },
    crumbs: [
      {
        parent: null,
        ref: "set_variable_ref",
        refIdx: 0,
        type: "SET_VARIABLE",
      },
      {
        parent: null,
        ref: "simple_ref",
        refIdx: 1,
        type: "SIMPLE",
      },
    ],
  },
  set_variable_ref_1: {
    task: {
      name: "set_variable_1",
      taskReferenceName: "set_variable_ref_1",
      type: "SET_VARIABLE",
      inputParameters: {
        year: "2024",
      },
    },
    crumbs: [
      {
        parent: null,
        ref: "set_variable_ref",
        refIdx: 0,
        type: "SET_VARIABLE",
      },
      {
        parent: null,
        ref: "simple_ref",
        refIdx: 1,
        type: "SIMPLE",
      },
      {
        parent: null,
        ref: "set_variable_ref_1",
        refIdx: 2,
        type: "SET_VARIABLE",
      },
    ],
  },
  join_ref: {
    task: {
      name: "join",
      taskReferenceName: "join_ref",
      inputParameters: {
        "Some-key-j8nkd": "${workflow.variables.year}",
      },
      type: "JOIN",
      joinOn: [],
      optional: false,
      asyncComplete: false,
    },
    crumbs: [
      {
        parent: null,
        ref: "set_variable_ref",
        refIdx: 0,
        type: "SET_VARIABLE",
      },
      {
        parent: null,
        ref: "simple_ref",
        refIdx: 1,
        type: "SIMPLE",
      },
      {
        parent: null,
        ref: "set_variable_ref_1",
        refIdx: 2,
        type: "SET_VARIABLE",
      },
      {
        parent: null,
        ref: "join_ref",
        refIdx: 3,
        type: "JOIN",
      },
    ],
  },
};
const variablesForTasks = {
  set_variable_ref: [],
  simple_ref: ["name"],
  set_variable_ref_1: ["name"],
  join_ref: ["name", "year"],
};

const crumbMapsWithoutVariables = {
  query_processor_ref: {
    task: {
      name: "query_processor",
      taskReferenceName: "query_processor_ref",
      inputParameters: {
        workflowNames: [],
        statuses: [],
        correlationIds: [],
        queryType: "CONDUCTOR_API",
        startTimeFrom: 60,
        startTimeTo: 30,
        freeText: "automation test",
      },
      type: "QUERY_PROCESSOR",
    },
    crumbs: [
      {
        parent: null,
        ref: "query_processor_ref",
        refIdx: 0,
        type: "QUERY_PROCESSOR",
      },
    ],
  },
  http_ref: {
    task: {
      name: "http",
      taskReferenceName: "http_ref",
      type: "HTTP",
      inputParameters: {
        uri: "https://orkes-api-tester.orkesconductor.com/api",
        method: "GET",
        connectionTimeOut: 3000,
        readTimeOut: "3000",
        accept: "application/json",
        contentType: "application/json",
      },
    },
    crumbs: [
      {
        parent: null,
        ref: "query_processor_ref",
        refIdx: 0,
        type: "QUERY_PROCESSOR",
      },
      {
        parent: null,
        ref: "http_ref",
        refIdx: 1,
        type: "HTTP",
      },
    ],
  },
  inline_ref: {
    task: {
      name: "inline",
      taskReferenceName: "inline_ref",
      type: "INLINE",
      inputParameters: {
        expression: "(function(){ return $.value1 + $.value2;})();",
        evaluatorType: "graaljs",
        value1: 1,
        value2: 2,
      },
    },
    crumbs: [
      {
        parent: null,
        ref: "query_processor_ref",
        refIdx: 0,
        type: "QUERY_PROCESSOR",
      },
      {
        parent: null,
        ref: "http_ref",
        refIdx: 1,
        type: "HTTP",
      },
      {
        parent: null,
        ref: "inline_ref",
        refIdx: 2,
        type: "INLINE",
      },
    ],
  },
};
const variablesForTaskWithoutVariables = {
  query_processor_ref: [],
  http_ref: [],
  inline_ref: [],
};

describe("getVariablesForEachTasks", () => {
  it("Should return each task with possible references from all taks in crumb with set variable task", () => {
    const result = getVariablesForEachTasks(crumbMaps as unknown as CrumbMap);
    expect(result).toEqual(variablesForTasks);
  });
  it("Should return each task with possible references from all taks in crumb without set variable task", () => {
    const result = getVariablesForEachTasks(
      crumbMapsWithoutVariables as unknown as CrumbMap,
    );
    expect(result).toEqual(variablesForTaskWithoutVariables);
  });
});

describe("jakatraPathToPropertyPath", () => {
  it("Should return the property path from the jakatra path from a nested fork task", () => {
    const result = jakatraPathToPropertyPath(
      "update.workflowDefs[0].tasks[1].forkTasks[0].<list element>[0]",
    );
    expect(result).toEqual("[1].forkTasks[0][0]");
  });
  it("Should return the property path from the jakatra path from a non nested task", () => {
    const result = jakatraPathToPropertyPath("update.workflowDefs[0].tasks[1]");
    expect(result).toEqual("[1]");
  });
  it("Should return the property path from a task nested in a switch task decision case", () => {
    const result = jakatraPathToPropertyPath(
      "update.workflowDefs[0].tasks[1].decisionCases[switch_case].<map value>[0]",
    );
    expect(result).toEqual("[1].decisionCases[switch_case][0]");
  });

  it("Should return the property path from a task nested in a switch task default case", () => {
    const result = jakatraPathToPropertyPath(
      "update.workflowDefs[0].tasks[1].defaultCase[0]",
    );
    expect(result).toEqual("[1].defaultCase[0]");
  });
});

describe("serverValidationErrorToIndexMessage", () => {
  // Mock TaskDef array for tests
  const mockTasks = [
    { name: "task0", taskReferenceName: "task0_ref", type: "SIMPLE" } as any,
    { name: "task1", taskReferenceName: "task1_ref", type: "SIMPLE" } as any,
    { name: "task2", taskReferenceName: "task2_ref", type: "SIMPLE" } as any,
    { name: "task3", taskReferenceName: "task3_ref", type: "SIMPLE" } as any,
  ];

  it("should map validation errors with task indices to IndexMessage objects", () => {
    const errors = [
      { path: "update.workflowDefs[0].tasks[0]", message: "Name is required" },
      { path: "update.workflowDefs[0].tasks[2]", message: "Value is invalid" },
    ];
    const result = serverValidationErrorToIndexTask(errors as any, mockTasks);
    expect(result).toEqual([
      {
        path: "update.workflowDefs[0].tasks[0]",
        message: "Name is required",
        taskPath: "[0]",
        task: mockTasks[0],
      },
      {
        path: "update.workflowDefs[0].tasks[2]",
        message: "Value is invalid",
        taskPath: "[2]",
        task: mockTasks[2],
      },
    ]);
  });

  it("should skip errors without a task index in the path", () => {
    const errors = [
      { path: "update.workflowDefs[0]", message: "Name is required" },
      { path: "update.workflowDefs[0].tasks[1]", message: "Value is invalid" },
    ];
    const result = serverValidationErrorToIndexTask(errors as any, mockTasks);
    expect(result).toEqual([
      { path: "update.workflowDefs[0]", message: "Name is required" },
      {
        path: "update.workflowDefs[0].tasks[1]",
        message: "Value is invalid",
        taskPath: "[1]",
        task: mockTasks[1],
      },
    ]);
  });

  it("should handle missing message fields gracefully", () => {
    const errors = [{ path: "update.workflowDefs[0].tasks[3]" }];
    const result = serverValidationErrorToIndexTask(errors as any, mockTasks);
    expect(result).toEqual([
      {
        path: "update.workflowDefs[0].tasks[3]",
        taskPath: "[3]",
        task: mockTasks[3],
      },
    ]);
  });

  it("should return an empty array if no errors have a task index", () => {
    const errors = [
      { path: "workflow.input.name", message: "Name is required" },
      { path: "workflow.input.value", message: "Value is invalid" },
    ];
    const result = serverValidationErrorToIndexTask(errors as any, mockTasks);
    expect(result).toEqual([
      { path: "workflow.input.name", message: "Name is required" },
      { path: "workflow.input.value", message: "Value is invalid" },
    ]);
  });

  it("should handle paths with only the task index (e.g., 'tasks[0]')", () => {
    const errors = [
      { path: "tasks[0]", message: "General error for task 0" },
      { path: "tasks[1]", message: "General error for task 1" },
    ];
    const result = serverValidationErrorToIndexTask(errors as any, mockTasks);
    expect(result).toEqual([
      {
        path: "tasks[0]",
        message: "General error for task 0",
        taskPath: "[0]",
        task: mockTasks[0],
      },
      {
        path: "tasks[1]",
        message: "General error for task 1",
        taskPath: "[1]",
        task: mockTasks[1],
      },
    ]);
  });

  it("should extract the correct index from paths with prefixes before tasks[<index>]", () => {
    const errors = [
      { path: "update.workflowDefs[0].tasks[1]", message: "Error for task 1" },
      {
        path: "update.workflowDefs[2].tasks[3]",
        message: "Error for task 3",
      },
    ];
    const result = serverValidationErrorToIndexTask(errors as any, mockTasks);
    expect(result).toEqual([
      {
        path: "update.workflowDefs[0].tasks[1]",
        message: "Error for task 1",
        taskPath: "[1]",
        task: mockTasks[1],
      },
      {
        path: "update.workflowDefs[2].tasks[3]",
        message: "Error for task 3",
        taskPath: "[3]",
        task: mockTasks[3],
      },
    ]);
  });
});

describe("reverifyServerErrorsTaskChanges", () => {
  const mockTask1 = {
    name: "task1",
    taskReferenceName: "task1_ref",
    type: "SIMPLE",
  } as any;
  const mockTask2 = {
    name: "task2",
    taskReferenceName: "task2_ref",
    type: "SIMPLE",
  } as any;
  const mockUpdatedTask1 = { ...mockTask1, name: "task1_updated" } as any;

  const createValidationError = (overrides = {}) => ({
    id: ErrorIds.FLOW_ERROR,
    message: "Error message",
    type: ErrorTypes.WORKFLOW,
    severity: ErrorSeverity.ERROR,
    hint: "Test hint",
    ...overrides,
  });

  it("should filter out validation errors for tasks that have changed", () => {
    const serverErrors = [
      createValidationError({
        validationErrors: [
          {
            path: "update.workflowDefs[0].tasks[0]",
            message: "Error 1",
            taskPath: "[0]",
            task: mockTask1,
          },
          {
            path: "update.workflowDefs[0].tasks[1]",
            message: "Error 2",
            taskPath: "[1]",
            task: mockTask2,
          },
        ],
      }),
    ];
    const currentWorkflow = {
      tasks: [mockUpdatedTask1, mockTask2],
    };

    const result = reverifyServerErrorsTaskChanges(
      serverErrors,
      currentWorkflow,
    );
    expect(result).toEqual([
      {
        ...serverErrors[0],
        validationErrors: [
          {
            path: "update.workflowDefs[0].tasks[1]",
            message: "Error 2",
            taskPath: "[1]",
            task: mockTask2,
          },
        ],
      },
    ]);
  });

  it("should return undefined if all validation errors are filtered out", () => {
    const serverErrors = [
      createValidationError({
        validationErrors: [
          {
            path: "update.workflowDefs[0].tasks[0]",
            message: "Error 1",
            taskPath: "[0]",
            task: mockTask1,
          },
        ],
      }),
    ];
    const currentWorkflow = {
      tasks: [mockUpdatedTask1],
    };

    const result = reverifyServerErrorsTaskChanges(
      serverErrors,
      currentWorkflow,
    );
    expect(result).toBeUndefined();
  });

  it("should handle empty validation errors array", () => {
    const serverErrors = [
      createValidationError({
        validationErrors: [],
      }),
    ];
    const currentWorkflow = {
      tasks: [mockTask1],
    };

    const result = reverifyServerErrorsTaskChanges(
      serverErrors,
      currentWorkflow,
    );
    expect(result).toBeUndefined();
  });

  it("should handle undefined validation errors", () => {
    const serverErrors = [createValidationError()];
    const currentWorkflow = {
      tasks: [mockTask1],
    };

    const result = reverifyServerErrorsTaskChanges(
      serverErrors,
      currentWorkflow,
    );
    expect(result).toBeUndefined();
  });

  it("should handle empty server errors array", () => {
    const result = reverifyServerErrorsTaskChanges([], { tasks: [mockTask1] });
    expect(result).toBeUndefined();
  });
});
describe("filterServerErrorsNotPresentInNodes", () => {
  const mockTask1: TaskDef = {
    name: "task1",
    taskReferenceName: "task1_ref",
    type: TaskType.SIMPLE,
    startDelay: 0,
    joinOn: [],
    defaultExclusiveJoinTask: [],
    optional: false,
    asyncComplete: false,
    description: "Mock task 1",
  };
  const mockTask2: TaskDef = {
    name: "task2",
    taskReferenceName: "task2_ref",
    type: TaskType.SIMPLE,
    startDelay: 0,
    joinOn: [],
    defaultExclusiveJoinTask: [],
    optional: false,
    asyncComplete: false,
    description: "Mock task 2",
  };

  const createValidationError = (overrides = {}) => ({
    id: ErrorIds.FLOW_ERROR,
    message: "Error message",
    type: ErrorTypes.WORKFLOW,
    severity: ErrorSeverity.ERROR,
    hint: "Test hint",
    ...overrides,
  });

  const createNode = (task: TaskDef): NodeData<NodeTaskData<TaskDef>> => ({
    id: task.taskReferenceName,
    data: {
      task,
      crumbs: [],
      selected: false,
    },
  });

  it("should filter out validation errors for tasks that are not present in nodes", () => {
    const serverErrors = [
      createValidationError({
        validationErrors: [
          {
            path: "update.workflowDefs[0].tasks[0]",
            message: "Error 1",
            task: mockTask1,
          },
          {
            path: "update.workflowDefs[0].tasks[1]",
            message: "Error 2",
            task: mockTask2,
          },
        ],
      }),
    ];
    const nodes: NodeData<NodeTaskData<TaskDef>>[] = [createNode(mockTask2)];

    const result = filterServerErrorsNotPresentInNodes(serverErrors, nodes);
    expect(result).toEqual([
      {
        ...serverErrors[0],
        validationErrors: [
          {
            path: "update.workflowDefs[0].tasks[1]",
            message: "Error 2",
            task: mockTask2,
          },
        ],
      },
    ]);
  });

  it("should return undefined if all validation errors are filtered out", () => {
    const serverErrors = [
      createValidationError({
        validationErrors: [
          {
            path: "update.workflowDefs[0].tasks[0]",
            message: "Error 1",
            task: mockTask1,
          },
        ],
      }),
    ];
    const nodes: NodeData<NodeTaskData<TaskDef>>[] = [];

    const result = filterServerErrorsNotPresentInNodes(serverErrors, nodes);
    expect(result).toBeUndefined();
  });

  it("should handle validation errors without task property", () => {
    const serverErrors = [
      createValidationError({
        validationErrors: [
          {
            path: "update.workflowDefs[0]",
            message: "General workflow error",
          },
        ],
      }),
    ];
    const nodes: NodeData<NodeTaskData<TaskDef>>[] = [createNode(mockTask1)];

    const result = filterServerErrorsNotPresentInNodes(serverErrors, nodes);
    expect(result).toEqual([
      {
        ...serverErrors[0],
        validationErrors: [
          {
            path: "update.workflowDefs[0]",
            message: "General workflow error",
          },
        ],
      },
    ]);
  });

  it("should handle empty validation errors array", () => {
    const serverErrors = [
      createValidationError({
        validationErrors: [],
      }),
    ];
    const nodes: NodeData<NodeTaskData<TaskDef>>[] = [createNode(mockTask1)];

    const result = filterServerErrorsNotPresentInNodes(serverErrors, nodes);
    expect(result).toBeUndefined();
  });

  it("should handle undefined validation errors", () => {
    const serverErrors = [createValidationError()];
    const nodes: NodeData<NodeTaskData<TaskDef>>[] = [createNode(mockTask1)];

    const result = filterServerErrorsNotPresentInNodes(serverErrors, nodes);
    expect(result).toBeUndefined();
  });

  it("should handle empty server errors array", () => {
    const result = filterServerErrorsNotPresentInNodes(
      [],
      [createNode(mockTask1)],
    );
    expect(result).toBeUndefined();
  });
});
