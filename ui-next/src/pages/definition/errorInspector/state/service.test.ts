import { TaskDef, TaskType, WorkflowDef } from "types";
import { DEFAULT_WF_ATTRIBUTES } from "utils/constants";
import {
  simpleDiagram,
  unknownTaskTypeWf,
  workflowWithUnknownType,
} from "../../../../testData/diagramTests";
import {
  computeWorkflowErrors,
  convertToPropertyPath,
  createMainValidator,
  extractTaskReferenceNameFromTaskErrors,
  findTaskError,
  identifyErrorLocation,
  truncateToLastNumber,
} from "./schemaValidator";
import {
  buildInputParameterNotationTree,
  createJoinOnReferenceError,
  findMissingJoinOnReferences,
  findUnMatchedTaskReferences,
  isValidNestedVariable,
  removedTasksToUnmatchedReferences,
  taskReferenceProblemToTaskErrors,
  valueContainsTaskReference,
  valueContainsVariableTaskReference,
  workflowParameterToValidationError,
} from "./service";
import { ErrorIds } from "./types";

describe("Workflow Test", () => {
  describe("computeWorkflowErrors", () => {
    it("Should return null if no error was found", () => {
      const validation = createMainValidator(
        simpleDiagram as unknown as WorkflowDef,
      );
      expect(validation).toBeNull();
    });

    it("Should return the validate object on error or null otherwise", () => {
      const { name: _noname, ...otherWorkflowProps } = simpleDiagram;
      const validation = createMainValidator(
        otherWorkflowProps as unknown as WorkflowDef,
      );
      expect(validation).not.toBeNull();
    });
  });
  describe("identifyErrorLocation", () => {
    it("Should identify workflow as a unique location if the error is within the workflow", () => {
      const { name: _noname, ...otherWorkflowProps } = simpleDiagram;
      const validation = createMainValidator(
        otherWorkflowProps as unknown as WorkflowDef,
      );
      const result = identifyErrorLocation(validation);

      expect(result.workflowRoot.length).toBeTruthy();
    });
    it("Should identify workflowTask as a unique location of errors", () => {
      const validation = createMainValidator(
        workflowWithUnknownType as unknown as WorkflowDef,
      );
      const result = identifyErrorLocation(validation);

      expect(result.workflowTasks.length).toBeTruthy();
    });
    it("Should identify two types of error", () => {
      const { name: _ignoreName, ...otherWorkflowWithUnknownTypeProps } =
        workflowWithUnknownType;
      const validation = createMainValidator(
        otherWorkflowWithUnknownTypeProps as unknown as WorkflowDef,
      );
      const result = identifyErrorLocation(validation);

      expect(result.workflowTasks.length).toBeTruthy();
      expect(result.workflowTasks.length).toBeTruthy();
    });
  });

  describe("extractTaskReferenceNameFromTaskErrors", () => {
    it("Should extract the tasks with the error", () => {
      const validation = createMainValidator(
        workflowWithUnknownType as unknown as WorkflowDef,
      );
      const result = identifyErrorLocation(validation);

      expect(result.workflowTasks.length).toBeTruthy();
      const tasksWithPoblems = extractTaskReferenceNameFromTaskErrors(
        result.workflowTasks,
        workflowWithUnknownType as unknown as WorkflowDef,
      );
      expect(tasksWithPoblems.length).toBe(1);
    });
  });

  describe("findTaskError", () => {
    it("Should return an unknown-task-type error", () => {
      const taskWithUnknownType = {
        name: "image_convert_resize_jim",
        taskReferenceName: "image_convert_resize_ref",
        inputParameters: {},
        type: "UNKNOWN_TYPE",
        defaultExclusiveJoinTask: [],
        asyncComplete: false,
        loopOver: [],
      };
      const result = findTaskError(taskWithUnknownType as unknown as TaskDef);
      expect(result.length).toBe(1);
      expect(result[0].id).toEqual(ErrorIds.UNKNOWN_TASK_TYPE);
    });

    it("Should return an task-type-not-present error", () => {
      const taskWithUnknownType = {
        name: "image_convert_resize_jim",
        taskReferenceName: "image_convert_resize_ref",
        inputParameters: {},
        defaultExclusiveJoinTask: [],
        asyncComplete: false,
        loopOver: [],
      };
      const result = findTaskError(taskWithUnknownType as unknown as TaskDef);
      expect(result.length).toBe(1);
      expect(result[0].id).toEqual(ErrorIds.TASK_TYPE_NOT_PRESENT);
    });
    it("Should return a task-required-field-missing", () => {
      const taskWithNoName = {
        taskReferenceName: "image_convert_resize_ref",
        inputParameters: {},
        type: "SIMPLE",
        defaultExclusiveJoinTask: [],
        asyncComplete: false,
        loopOver: [],
      };
      const result = findTaskError(taskWithNoName as unknown as TaskDef);
      expect(result[0].id).toEqual(ErrorIds.TASK_REQUIRED_FIELD_MISSING);
    });

    it("Should not show taskError with unknown task type", () => {
      const result = computeWorkflowErrors(unknownTaskTypeWf as any);
      expect(result.taskErrors).toEqual([]);
    });

    // Skipping this test because after refactoring HTTP Task, the http_request is not required in inputParameters
    it.skip("Should return a task-required-input-parameter-field", () => {
      const httpTask = {
        name: "last_task",
        taskReferenceName: "last_task",
        inputParameters: {},
        type: "HTTP",
      };
      const result = findTaskError(httpTask as unknown as TaskDef);
      expect(result[0].id).toEqual(
        ErrorIds.TASK_REQUIRED_INPUT_PARAMETERS_MISSING,
      );
    });
  });
});

describe("findUnMatchedReferences", () => {
  it("Should return a list of containing tasks with unmatched references", () => {
    const affectedTasks = removedTasksToUnmatchedReferences({
      existingTaskReferences: ["image_convert_resize_ref", "upload_toS3_ref"],
      lastTaskRoute: simpleDiagram.tasks as unknown as TaskDef[],
    });

    expect(affectedTasks.length).toBe(1);
  });
});

describe("valueContainsTaskReference", () => {
  const path = "somePath";
  it("Should return path of reference within a string if no match found", () => {
    const result = valueContainsTaskReference(
      "${myTestReferenceUnkn.output.fileLocation}",
      ["myTestReference"],
      path,
    );
    expect(result).toEqual([path]);
  });
  it("Should return path if there is no reference within a string array", () => {
    expect(
      valueContainsTaskReference(
        ["${myTestReferenceUNK.output.fileLocation}"],
        ["myTestReference"],
        path,
      ),
    ).toEqual([path]);
  });
  it("Should return path with nested key if there is no reference within an object value", () => {
    expect(
      valueContainsTaskReference(
        { a: "${myTestReference.output.fileLocation}" },
        ["myTest"],
        path,
      ),
    ).toEqual([`${path}.a`]);
  });

  it("Should return path if there is no reference within a nested object value", () => {
    expect(
      valueContainsTaskReference(
        { a: { b: "${myTestReference.output.fileLocation}" } },
        ["myTest"],
        path,
      ),
    ).toEqual([`${path}.a.b`]);
  });

  it("Should return empty if there is no reference to task inLocation", () => {
    expect(
      valueContainsTaskReference(
        {
          a: { b: "${myTestReferenceThat does not exist.output.fileLocation}" },
        },
        ["myTestReference"],
        "i",
      ),
    ).toEqual(["i.a.b"]);
  });
  it("Should return path if extra space found", () => {
    const result = valueContainsTaskReference(
      "${ myTestReference.output }",
      ["myTestReference"],
      path,
    );
    expect(result).toEqual([path]);
  });
  it("Should pass the check if [] found", () => {
    const result = valueContainsTaskReference(
      "${myTestReference.output.test[0]}",
      ["myTestReference"],
      path,
    );
    expect(result).toEqual([]);
  });
  it("Should return path if nested ${} found", () => {
    const result = valueContainsTaskReference(
      "${${myTestReference.output}}",
      ["myTestReference"],
      path,
    );
    expect(result).toEqual([path]);
  });
  it("Should pass if single hyphen found", () => {
    const result = valueContainsTaskReference(
      "${myTestReference.output-iid}",
      ["myTestReference"],
      path,
    );
    expect(result).toEqual([]);
  });
  it("Should return path if sequence of hyphen found", () => {
    const result = valueContainsTaskReference(
      "${myTestReference.output--iid}",
      ["myTestReference"],
      path,
    );
    expect(result).toEqual([path]);
  });
  it("Should return path if special characters found", () => {
    const result = valueContainsTaskReference(
      "${myTestReference.output#?@%&}",
      ["myTestReference"],
      path,
    );
    expect(result).toEqual([path]);
  });
  it("Should return path if space inbetween", () => {
    const result = valueContainsTaskReference(
      "${myTestReference .output}",
      ["myTestReference"],
      path,
    );
    expect(result).toEqual([path]);
  });
  it("Should return legacy error if ${CPEWF_TASK_ID} found(workflow missing references)", () => {
    const result = workflowParameterToValidationError({
      data: "${CPEWF_TASK_ID}",
      workflowName: "some_workflow",
    });
    const expectedMessage =
      "'data' references '${CPEWF_TASK_ID}', is a legacy ref and should be replaced by 'task_ref_name.taskId'";
    expect(result.message).toEqual(expectedMessage);
  });
  it("Should return legacy error if ${CPEWF_TASK_ID} found(task missing references)", () => {
    const result = taskReferenceProblemToTaskErrors({
      task: {
        name: "get_random_fact",
        taskReferenceName: "get_random_fact",
        description: "",
        startDelay: 0,
        joinOn: [""],
        optional: false,
        defaultExclusiveJoinTask: [""],
        inputParameters: {
          http_request: {
            uri: "https://orkes-api-tester.orkesconductor.com/api",
            method: "GET",
            connectionTimeOut: 3000,
            readTimeOut: 3000,
            accept: "application/json",
            contentType: "application/json",
            body: "${CPEWF_TASK_ID}",
          },
        },
        type: TaskType.HTTP,
      },
      parameters: ["http_request.body"],
      expressions: [],
    });

    const expectedMessage =
      "input parameter 'http_request.body' references '${CPEWF_TASK_ID}', A legacy ref and should be replaced by 'task_ref_name.taskId'";
    expect(result.errors[0]?.message).toEqual(expectedMessage);
  });
  it("Should pass if default workflow variables are found", () => {
    const taskReferences = [
      "workflow.workflowId",
      "workflow.output",
      "workflow.status",
      "workflow.parentWorkflowId",
      "workflow.parentWorkflowTaskId",
      "workflow.workflowType",
      "workflow.version",
      "workflow.correlationId",
      "workflow.variables",
      "workflow.createTime",
      "workflow.taskToDomain",
    ];
    DEFAULT_WF_ATTRIBUTES.forEach((item) => {
      const result = valueContainsTaskReference(
        `\${${item}}`,
        taskReferences,
        path,
      );
      expect(result).toEqual([]);
    });
  });
});

describe("valueContainsVariableTaskReference", () => {
  const path = "somePath";
  const stringValue = "${workflow.variables.count}";
  const noRefValue = "${workflow.variables.something}";
  const nestedArray = [
    "${workflow.variables.name}",
    "${workflow.variables.types}",
    ["${workflow.variables.type}", "${workflow.variables.type}"],
  ];
  const nestedObject = {
    value1: "${workflow.variables.year}",
    value2: "${workflow.variables.location}",
    value3: {
      innerValue1: "${workflow.variables.years}",
      innerValue2: "${workflow.variables.location}",
    },
  };
  const variableReferences = [
    "workflow.variables.name",
    "workflow.variables.type",
    "workflow.variables.year",
    "workflow.variables.location",
    "workflow.variables.count",
  ];

  it("Should return empty if there is no reference to task inLocation", () => {
    expect(
      valueContainsVariableTaskReference(stringValue, variableReferences, path),
    ).toEqual([]);
  });
  it("Should return path of reference within a string if no match found", () => {
    const result = valueContainsVariableTaskReference(
      noRefValue,
      variableReferences,
      path,
    );
    expect(result).toEqual([path]);
  });
  it("Should return path if there is no reference within a nested array", () => {
    expect(
      valueContainsVariableTaskReference(nestedArray, variableReferences, path),
    ).toEqual([path]);
  });
  it("Should return path with nested key if there is no reference within an nested object", () => {
    expect(
      valueContainsVariableTaskReference(
        nestedObject,
        variableReferences,
        path,
      ),
    ).toEqual([`${path}.value3.innerValue1`]);
  });

  it("Should show taskReferences with list of pathsAffected", () => {
    const affectedTask = {
      name: "upload_toS3_jim",
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
    } as unknown as TaskDef;
    const result = findUnMatchedTaskReferences(
      ["upload_toS3_ref"],
      [affectedTask],
    );
    expect(result).toEqual(
      expect.arrayContaining([
        {
          task: affectedTask,
          parameters: ["fileLocation"],
          expressions: [],
          joinOn: [],
        },
      ]),
    );
  });
});

describe("buildInputParameterNotationTree", () => {
  const path = "somePath";
  it("Should return path if value is just a string", () => {
    const result = buildInputParameterNotationTree({ a: "justAString" }, path);
    expect(result).toEqual([path + ".a"]);
  });
  it("Should return path if value is an array", () => {
    expect(
      buildInputParameterNotationTree(
        { a: ["${myTestReferenceUNK.output.fileLocation}"] },
        path,
      ),
    ).toEqual([path + ".a"]);
  });

  it("Should return path if value is in a nested object", () => {
    expect(
      buildInputParameterNotationTree(
        { a: { b: "${myTestReference.output.fileLocation}" } },
        path,
      ),
    ).toEqual([`${path}.a.b`]);
  });
  it("Should return empty if provided object is empty", () => {
    expect(buildInputParameterNotationTree({}, path)).toEqual([]);
  });
  it("Should return two paths even if one is empty", () => {
    expect(buildInputParameterNotationTree({ a: "some", b: {} }, path)).toEqual(
      [`${path}.a`, `${path}.b`],
    );
  });
});

describe("truncateToLastNumber", () => {
  it("Should return path to last number", () => {
    expect(
      truncateToLastNumber("/decisionCases/new_case_bmoty/0/inputParameters"),
    ).toEqual("/decisionCases/new_case_bmoty/0");
  });
  it("Should support more than one path", () => {
    expect(
      truncateToLastNumber(
        "/decisionCases/new_case_bmoty/0/decsionCases/otherPath/1/type",
      ),
    ).toEqual("/decisionCases/new_case_bmoty/0/decsionCases/otherPath/1");
  });
});

describe("convertToPropertyPath", () => {
  it("Should take an instancePath and convert it to a property path", () => {
    const instancePath = "/decisionCases/new_case_bmoty/0/inputParameters";
    const longInstancePath =
      "/decisionCases/new_case_bmoty/0/decsionCases/otherPath/1";
    expect(convertToPropertyPath(instancePath)).toEqual(
      ".decisionCases.new_case_bmoty[0].inputParameters",
    );
    expect(convertToPropertyPath(longInstancePath)).toEqual(
      ".decisionCases.new_case_bmoty[0].decsionCases.otherPath[1]",
    );
  });
});

describe("isValidNestedVariable", () => {
  const expectedReferences = [
    "workflow.input",
    "workflow.input.test",
    "workflow.secrets",
  ];
  it("Should return true as variables nested are available", () => {
    const valueString = "${workflow.secrets.${workflow.input.test}}";
    expect(isValidNestedVariable(expectedReferences, valueString)).toEqual(
      true,
    );
  });
  it("Should return false as some variable is not available", () => {
    const valueString1 = "${workflow.secrets.${workflow.input.cool}}";
    expect(isValidNestedVariable(expectedReferences, valueString1)).toEqual(
      false,
    );
  });
  it("Should return true - nested variables inside the url", () => {
    const valueString3 =
      "https://orkes-api-tester.orkesconductor.com/api/${workflow.secrets.${workflow.input.test}}";
    expect(isValidNestedVariable(expectedReferences, valueString3)).toEqual(
      true,
    );
  });
});

describe("findMissingJoinOnReferences", () => {
  const baseTask = {
    name: "dummy",
    taskReferenceName: "dummy_ref",
    description: "",
    startDelay: 0,
    inputParameters: {},
    optional: false,
    asyncComplete: false,
    defaultExclusiveJoinTask: [],
    loopOver: [],
    decisionCases: {},
    defaultCase: [],
    forkTasks: [],
    type: undefined,
    joinOn: [],
  };

  const task = {
    ...baseTask,
    name: "join",
    taskReferenceName: "join_ref",
    type: TaskType.JOIN,
    joinOn: ["a", "b", "c"],
  };
  it("returns missing joinOn references", () => {
    expect(findMissingJoinOnReferences(task, ["a", "c"])).toEqual(["b"]);
  });

  it("returns empty array if all joinOn references exist", () => {
    const taskAllExist = {
      ...baseTask,
      type: TaskType.JOIN,
      joinOn: ["a", "b"],
    };
    expect(findMissingJoinOnReferences(taskAllExist, ["a", "b"])).toEqual([]);
  });

  it("returns empty array if not a JOIN task", () => {
    const notJoinTask = {
      ...baseTask,
      type: TaskType.SIMPLE,
      joinOn: ["a", "b"],
    };
    expect(findMissingJoinOnReferences(notJoinTask, ["a", "b"])).toEqual([]);
  });
});

describe("createJoinOnReferenceError", () => {
  const baseTask = {
    name: "dummy",
    taskReferenceName: "dummy_ref",
    description: "",
    startDelay: 0,
    inputParameters: {},
    optional: false,
    asyncComplete: false,
    defaultExclusiveJoinTask: [],
    loopOver: [],
    decisionCases: {},
    defaultCase: [],
    forkTasks: [],
    type: undefined,
    joinOn: [],
  };
  it("returns a structured error for missing joinOn reference", () => {
    const task = {
      ...baseTask,
      name: "join",
      taskReferenceName: "join_ref",
      type: TaskType.JOIN,
      joinOn: ["a", "b", "c"],
    };
    const missingRef = "missing_task";
    expect(createJoinOnReferenceError(task, missingRef)).toEqual({
      id: "reference-problems",
      taskReferenceName: "join_ref",
      message: "joinOn references missing taskReferenceName 'missing_task'",
      type: "TASK",
      severity: "WARNING",
    });
  });
});
