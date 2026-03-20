import { TaskDef } from "types/common";
import { extractVariablesFromTask } from "./helpers";

const tasks = [
  {
    name: "set_variable",
    taskReferenceName: "set_variable_ref",
    type: "SET_VARIABLE",
    inputParameters: {
      name: "Orkes",
    },
  },
  {
    name: "query_processor",
    taskReferenceName: "query_processor_ref",
    inputParameters: {
      workflowNames: [],
      statuses: ["FAILED"],
      correlationIds: [],
      queryType: "CONDUCTOR_API",
      freeText: "automation test",
    },
    type: "QUERY_PROCESSOR",
    decisionCases: {},
    defaultCase: [],
    forkTasks: [],
    startDelay: 0,
    joinOn: [],
    optional: false,
    defaultExclusiveJoinTask: [],
    asyncComplete: false,
    loopOver: [],
    onStateChange: {},
  },
  {
    name: "inline",
    taskReferenceName: "inline_ref",
    inputParameters: {
      expression:
        "(function(){ \n  const nameAndVersions = $.results.map(({workflowType,version})=>({name:workflowType,version})); \n  return nameAndVersions;\n  })();",
      evaluatorType: "graaljs",
      results: "${query_processor_ref.output.result.workflows}",
    },
    type: "INLINE",
    decisionCases: {},
    defaultCase: [],
    forkTasks: [],
    startDelay: 0,
    joinOn: [],
    optional: false,
    defaultExclusiveJoinTask: [],
    asyncComplete: false,
    loopOver: [],
    onStateChange: {},
  },
];

const taskWithoutVariables = [
  {
    name: "query_processor",
    taskReferenceName: "query_processor_ref",
    inputParameters: {
      workflowNames: [],
      statuses: ["FAILED"],
      correlationIds: [],
      queryType: "CONDUCTOR_API",
      freeText: "automation test",
    },
    type: "QUERY_PROCESSOR",
    decisionCases: {},
    defaultCase: [],
    forkTasks: [],
    startDelay: 0,
    joinOn: [],
    optional: false,
    defaultExclusiveJoinTask: [],
    asyncComplete: false,
    loopOver: [],
    onStateChange: {},
  },
  {
    name: "inline",
    taskReferenceName: "inline_ref",
    inputParameters: {
      expression:
        "(function(){ \n  const nameAndVersions = $.results.map(({workflowType,version})=>({name:workflowType,version})); \n  return nameAndVersions;\n  })();",
      evaluatorType: "graaljs",
      results: "${query_processor_ref.output.result.workflows}",
    },
    type: "INLINE",
    decisionCases: {},
    defaultCase: [],
    forkTasks: [],
    startDelay: 0,
    joinOn: [],
    optional: false,
    defaultExclusiveJoinTask: [],
    asyncComplete: false,
    loopOver: [],
    onStateChange: {},
  },
];

describe("extractVariablesFromTask", () => {
  it("Extract variables from tasks with set variable tasks", () => {
    const result = extractVariablesFromTask(tasks as unknown as TaskDef[]);
    expect(result).toEqual(["name"]);
  });
  it("Extract variables from tasks without set variable tasks", () => {
    const result = extractVariablesFromTask(
      taskWithoutVariables as unknown as TaskDef[],
    );
    expect(result).toEqual([]);
  });
});
