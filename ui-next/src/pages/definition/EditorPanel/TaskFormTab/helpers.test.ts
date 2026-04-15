import { TaskDef, TaskType } from "types/common";
import {
  getCorrespondingJoinTask,
  updateInputParametersCommon,
} from "./helpers";

const taskJson = {
  name: "start_workflow",
  taskReferenceName: "start_workflow_ref",
  inputParameters: {
    startWorkflow: {
      name: "SUB_WORKFLOW_TASK_TEST_WF",
      input: {},
      version: 1,
    },
  },
  type: TaskType.START_WORKFLOW,
};

const task = {
  name: "start_workflow",
  taskReferenceName: "start_workflow_ref",
  inputParameters: {
    startWorkflow: {
      name: "",
      input: {},
    },
  },
  type: TaskType.START_WORKFLOW,
};

const getWorkflowDefinitionByNameAndVersionFn: any = (_params: any) => {
  return {
    createTime: 1709828406534,
    updateTime: 1709828406538,
    name: "SUB_WORKFLOW_TASK_TEST_WF",
    description: "donot delete this workflow. used for tests.",
    version: 1,
    tasks: [
      {
        name: "http",
        taskReferenceName: "http_ref",
        inputParameters: {
          uri: "https://orkes-api-tester.orkesconductor.com/api",
          method: "GET",
          connectionTimeOut: 3000,
          readTimeOut: "3000",
          accept: "application/json",
          contentType: "application/json",
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
        onStateChange: {},
      },
    ],
    inputParameters: ["Name", "Age", "Address"],
    outputParameters: {},
    failureWorkflow: "",
    schemaVersion: 2,
    restartable: true,
    workflowStatusListenerEnabled: false,
    ownerEmail: "najeeb.thangal@orkes.io",
    timeoutPolicy: "ALERT_ONLY",
    timeoutSeconds: 0,
    variables: {},
    inputTemplate: {},
  };
};

const expectedResult = {
  name: "start_workflow",
  taskReferenceName: "start_workflow_ref",
  inputParameters: {
    startWorkflow: {
      name: "SUB_WORKFLOW_TASK_TEST_WF",
      input: {
        Name: "",
        Age: "",
        Address: "",
      },
      version: 1,
    },
  },
  type: "START_WORKFLOW",
};

describe("updateInputParametersCommon", () => {
  it("return expected result", () => {
    const onChangePromise = new Promise((resolve) => {
      const onChange = (data: Partial<TaskDef>) => {
        resolve({ ...data });
      };

      updateInputParametersCommon(
        taskJson,
        task,
        {},
        onChange,
        "inputParameters.startWorkflow",
        "inputParameters.startWorkflow.input",
        TaskType.START_WORKFLOW,
        getWorkflowDefinitionByNameAndVersionFn,
      );
    });

    return onChangePromise.then((result: any) => {
      expect(result).toEqual(expectedResult);
    });
  });
});

describe("getCorrespondingJoinTask", () => {
  it("return corresponding join task of the fork", () => {
    const originalTask = {
      taskReferenceName: "fork_join",
      type: TaskType.FORK_JOIN,
    };
    const tasksList = [
      { taskReferenceName: "http", type: TaskType.HTTP },
      { taskReferenceName: "fork_join", type: TaskType.FORK_JOIN },
      { taskReferenceName: "join", type: TaskType.JOIN },
    ];
    const expectedResult = [{ taskReferenceName: "join", type: TaskType.JOIN }];
    const correspondingJoinTask = getCorrespondingJoinTask(
      originalTask,
      tasksList,
    );
    expect(correspondingJoinTask).toEqual(expectedResult);
  });
  it("return empty array if corresponding join task of the fork not found", () => {
    const originalTask = {
      taskReferenceName: "fork_join_1",
      type: TaskType.FORK_JOIN,
    };
    const tasksList = [
      { taskReferenceName: "http", type: TaskType.HTTP },
      { taskReferenceName: "fork_join", type: TaskType.FORK_JOIN },
      { taskReferenceName: "join", type: TaskType.JOIN },
    ];
    const correspondingJoinTask = getCorrespondingJoinTask(
      originalTask,
      tasksList,
    );
    expect(correspondingJoinTask).toEqual([]);
  });
  it("return corresponding join task of the fork - multiple fork joins are present", () => {
    const originalTask = {
      taskReferenceName: "fork_join_2",
      type: TaskType.FORK_JOIN,
    };
    const tasksList = [
      { taskReferenceName: "http", type: TaskType.HTTP },
      { taskReferenceName: "fork_join", type: TaskType.FORK_JOIN },
      { taskReferenceName: "join", type: TaskType.JOIN },
      { taskReferenceName: "http_3", type: TaskType.HTTP },
      { taskReferenceName: "http_1", type: TaskType.HTTP },
      { taskReferenceName: "fork_join_2", type: TaskType.FORK_JOIN },
      { taskReferenceName: "join_2", type: TaskType.JOIN },
      { taskReferenceName: "http_3", type: TaskType.HTTP },
    ];
    const expectedResult = [
      { taskReferenceName: "join_2", type: TaskType.JOIN },
    ];
    const correspondingJoinTask = getCorrespondingJoinTask(
      originalTask,
      tasksList,
    );
    expect(correspondingJoinTask).toEqual(expectedResult);
  });
  it("return empty array if tasksList is undefined", () => {
    const originalTask = {
      taskReferenceName: "fork_join",
      type: TaskType.FORK_JOIN,
    };
    const tasksList = undefined;
    const correspondingJoinTask = getCorrespondingJoinTask(
      originalTask,
      tasksList,
    );
    expect(correspondingJoinTask).toEqual([]);
  });
});
