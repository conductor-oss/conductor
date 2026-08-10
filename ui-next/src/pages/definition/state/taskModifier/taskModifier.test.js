import {
  populationMinMax,
  simpleDiagram,
  simpleLoopSample,
  switchExample,
} from "testData/diagramTests";
import {
  ADD_NEW_SWITCH_PATH,
  ADD_TASK,
  ADD_TASK_ABOVE,
  DELETE_TASK,
  REPLACE_TASK,
} from "./constants";
import {
  applyAddTask,
  applyOperationArrayOnTasks,
  findTaskModificationPath,
  moveTask,
  updateTaskReferenceName,
} from "./taskModifier";

describe("Task modifier", () => {
  it("should find the cooresponding task array for a given task", () => {
    const sampleCrumbs = [
      { parent: null, ref: "__start", refIdx: 0 },
      { parent: null, ref: "my_fork_join_ref", refIdx: 1 },
      { parent: "my_fork_join_ref", ref: "loop_2", refIdx: 0 },
      { parent: "loop_2", ref: "loop_2_task_iter", refIdx: 0 },
      { parent: "loop_2", ref: "loop_2_sv", refIdx: 0 },
    ];
    expect(findTaskModificationPath(sampleCrumbs, "loop_2_sv")).toEqual([
      { parent: null, ref: "my_fork_join_ref", refIdx: 1 },
      { parent: "my_fork_join_ref", ref: "loop_2", refIdx: 0 },
      { parent: "loop_2", ref: "loop_2_sv", refIdx: 0 },
    ]);
  });

  it("Should add an item to tasks array", () => {
    const forkTaskIdxInTasks = 1;
    const crumbs = [
      { parent: null, ref: "my_fork_join_ref", refIdx: forkTaskIdxInTasks },
    ];
    const taskToAdd = {
      name: "sample_task_name_1",
      taskReferenceName: "sample_task_name_a_ref",
      type: "SIMPLE",
    };

    const modifiedTasks = applyOperationArrayOnTasks(
      crumbs,
      simpleLoopSample.tasks,
      { type: ADD_TASK_ABOVE, payload: taskToAdd },
    );

    expect(modifiedTasks).toEqual(expect.arrayContaining([]));
  });

  it("Should Add an item to a nested task within fork and loop", () => {
    const forkTaskIdxWhereLoop2Is = 1;
    const forkTaskIdxInTasks = 1;
    const loopTaskIdxWithinForkTasks = 0;
    const loop2InnerTaskIndexToApplyOperationOn = 0;
    const taskToAdd = {
      name: "sample_task_name_0",
      taskReferenceName: "sample_task_name_0_ref",
      type: "SIMPLE",
    };
    const wfTasks = applyOperationArrayOnTasks(
      [
        { parent: null, ref: "my_fork_join_ref", refIdx: forkTaskIdxInTasks },
        {
          parent: "my_fork_join_ref",
          ref: "loop_2",
          refIdx: loopTaskIdxWithinForkTasks,
        },
        {
          parent: "loop_2",
          ref: "loop_2_sv",
          refIdx: loop2InnerTaskIndexToApplyOperationOn,
        },
      ],
      simpleLoopSample.tasks,
      { type: ADD_TASK_ABOVE, payload: taskToAdd },
    );

    const forkJoinTask = wfTasks[forkTaskIdxInTasks];
    const targetForkTasks = forkJoinTask.forkTasks[forkTaskIdxWhereLoop2Is];
    const loop2Task = targetForkTasks[loopTaskIdxWithinForkTasks];
    const loopingTasks = loop2Task.loopOver;

    expect(loopingTasks).toEqual(expect.arrayContaining([taskToAdd]));
  });
  it("Should be able to delete a SIMPLE task", () => {
    const simpleTaskCrumbsPath = [
      {
        parent: null,
        ref: "image_convert_resize_ref",
        refIdx: 0,
      },
    ];
    const wfTasks = applyOperationArrayOnTasks(
      simpleTaskCrumbsPath,
      simpleDiagram.tasks,
      { type: DELETE_TASK },
    );
    expect(
      wfTasks.map(({ taskReferenceName }) => taskReferenceName),
    ).not.toEqual(expect.arrayContaining(["image_convert_resize_ref"]));
  });

  it("Should be able to delete a Fork Task. Deleting FORK should also delete JOIN", () => {
    const forkTaskToDeleteModificationPath = [
      {
        parent: null,
        ref: "fork_ref",
        refIdx: 2,
      },
    ];
    const wfTasks = applyOperationArrayOnTasks(
      forkTaskToDeleteModificationPath,
      populationMinMax.tasks,
      { type: DELETE_TASK },
    );
    expect(wfTasks.map(({ taskReferenceName }) => taskReferenceName)).toEqual([
      "get_population_data_ref",
    ]);
  });
  it("Should be able to add a SWITCH branch if task is type SWITCH", () => {
    const taskContaingSwitch = [
      {
        parent: null,
        ref: "switch_task",
        refIdx: 1,
      },
    ];
    const wfTasks = applyOperationArrayOnTasks(
      taskContaingSwitch,
      switchExample.tasks,
      {
        type: ADD_NEW_SWITCH_PATH,
        payload: {
          branchName: "hasName",
        },
      },
    );
    const [resultTask] = wfTasks.filter(
      ({ taskReferenceName }) => taskReferenceName === "switch_task",
    );
    expect(Object.keys(resultTask.decisionCases)).toEqual(
      expect.arrayContaining(["hasName"]),
    );
    // console.log(JSON.stringify(wfTasks, null, 2));
  });
  it("Should replace the task in the tree by the task sent in payload", () => {
    const forkTaskToDeleteModificationPath = [
      {
        parent: null,
        ref: "get_population_data_ref",
        refIdx: 0,
      },
    ];

    const wfTasks = applyOperationArrayOnTasks(
      forkTaskToDeleteModificationPath,
      populationMinMax.tasks,
      {
        type: REPLACE_TASK,
        payload: {
          name: "get_population_data",
          taskReferenceName: "get_population_different_ref",
          inputParameters: {
            http_request: {
              uri: "https://datausa.io/api/data?drilldowns=State&measures=Population&year=latest",
              method: "GET",
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
      },
    );
    expect(wfTasks[0].taskReferenceName).toBe("get_population_different_ref");
  });
  it("Should prepend the task to the default branch in switch when adding a task", () => {
    const taskContaingSwitch = [
      {
        parent: null,
        ref: "switch_task",
        refIdx: 1,
        onDecisionBranch: "defaultCase",
      },
    ];
    const wfTasks = applyOperationArrayOnTasks(
      taskContaingSwitch,
      switchExample.tasks,
      {
        type: ADD_TASK,
        payload: {
          name: "prepended",
          taskReferenceName: "prepended_task",
          inputParameters: {},
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
      },
    );
    const [resultTask] = wfTasks.filter(
      ({ taskReferenceName }) => taskReferenceName === "switch_task",
    );
    expect(resultTask.defaultCase.length).toBe(2);
    expect(
      resultTask.defaultCase.map(({ taskReferenceName }) => taskReferenceName),
    ).toEqual(["prepended_task", "task_8_default"]);
  });

  it("Should be able to add the task to defaulCase even if defaultCase is empty", () => {
    const taskContaingSwitch = [
      {
        parent: null,
        ref: "switch_task",
        refIdx: 1,
        onDecisionBranch: "defaultCase",
      },
    ];
    const modifiedExampleEmptyDefaultCase = {
      ...switchExample,
      tasks: switchExample.tasks.map((task) =>
        task.type === "SWITCH" ? { ...task, defaultCase: [] } : task,
      ),
    };
    const wfTasks = applyOperationArrayOnTasks(
      taskContaingSwitch,
      modifiedExampleEmptyDefaultCase.tasks,
      {
        type: ADD_TASK,
        payload: {
          name: "prepended",
          taskReferenceName: "prepended_task",
          inputParameters: {},
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
      },
    );
    const [resultTask] = wfTasks.filter(
      ({ taskReferenceName }) => taskReferenceName === "switch_task",
    );
    expect(resultTask.defaultCase.length).toBe(1);
    expect(
      resultTask.defaultCase.map(({ taskReferenceName }) => taskReferenceName),
    ).toEqual(["prepended_task"]);
  });
  it("Should add a dynamic fork task into an empty defaultCase of switch task", () => {
    const taskArray = [
      {
        name: "switch",
        taskReferenceName: "switch_ref",
        inputParameters: {
          switchCaseValue: "",
        },
        type: "SWITCH",
        decisionCases: {},
        defaultCase: [],
        evaluatorType: "value-param",
        expression: "switchCaseValue",
      },
    ];
    const idx = 0;
    const payload = [
      {
        name: "fork_join_dynamic",
        taskReferenceName: "fork_join_dynamic_ref",
        inputParameters: {
          dynamicTasks: "",
          dynamicTasksInput: "",
        },
        type: "FORK_JOIN_DYNAMIC",
        dynamicForkTasksParam: "dynamicTasks",
        dynamicForkTasksInputParamName: "dynamicTasksInput",
        startDelay: 0,
        optional: false,
        asyncComplete: false,
      },
      {
        name: "join",
        taskReferenceName: "join_ref",
        inputParameters: {},
        type: "JOIN",
        joinOn: [],
        optional: false,
        asyncComplete: false,
      },
    ];
    const crumbProps = {
      parent: null,
      ref: "switch_ref",
      type: "SWITCH",
      onDecisionBranch: "defaultCase",
    };
    const result = applyAddTask(taskArray, idx, payload, crumbProps);
    const expectedResult = [
      {
        name: "switch",
        taskReferenceName: "switch_ref",
        inputParameters: {
          switchCaseValue: "",
        },
        type: "SWITCH",
        decisionCases: {},
        defaultCase: [
          {
            name: "fork_join_dynamic",
            taskReferenceName: "fork_join_dynamic_ref",
            inputParameters: {
              dynamicTasks: "",
              dynamicTasksInput: "",
            },
            type: "FORK_JOIN_DYNAMIC",
            dynamicForkTasksParam: "dynamicTasks",
            dynamicForkTasksInputParamName: "dynamicTasksInput",
            startDelay: 0,
            optional: false,
            asyncComplete: false,
          },
          {
            name: "join",
            taskReferenceName: "join_ref",
            inputParameters: {},
            type: "JOIN",
            joinOn: [],
            optional: false,
            asyncComplete: false,
          },
        ],
        evaluatorType: "value-param",
        expression: "switchCaseValue",
      },
    ];
    expect(result).toEqual(expectedResult);
  });
});

describe("moveTask", () => {
  it("Should be able to drag a task inside an empty doWhile", () => {
    const workflowChanges = {
      name: "NewWorkflow_2qs7k",
      description: "",
      version: 1,
      tasks: [
        {
          name: "simple",
          taskReferenceName: "simple_ref",
          type: "SIMPLE",
        },
        {
          name: "do_while",
          taskReferenceName: "do_while_ref",
          inputParameters: {},
          type: "DO_WHILE",
          startDelay: 0,
          optional: false,
          asyncComplete: false,
          loopCondition: "",
          evaluatorType: "value-param",
          loopOver: [],
        },
      ],
      inputParameters: [],
      outputParameters: {},
      schemaVersion: 2,
      restartable: true,
      workflowStatusListenerEnabled: false,
      ownerEmail: "najeeb.thangal@orkes.io",
      timeoutPolicy: "ALERT_ONLY",
      timeoutSeconds: 0,
      failureWorkflow: "",
    };
    const sourceTask = {
      name: "simple",
      taskReferenceName: "simple_ref",
      type: "SIMPLE",
    };
    const sourceTaskCrumbs = [
      {
        parent: null,
        ref: "simple_ref",
        refIdx: 0,
        type: "SIMPLE",
      },
    ];
    const targetTask = {
      name: "do_while",
      taskReferenceName: "do_while_ref",
      inputParameters: {},
      type: "DO_WHILE",
      startDelay: 0,
      optional: false,
      asyncComplete: false,
      loopCondition: "",
      evaluatorType: "value-param",
      loopOver: [],
    };
    const targetLocationCrumbs = [
      {
        parent: null,
        ref: "simple_ref",
        refIdx: 0,
        type: "SIMPLE",
      },
      {
        parent: null,
        ref: "do_while_ref",
        refIdx: 1,
        type: "DO_WHILE",
      },
    ];
    const position = "ADD_TASK_IN_DO_WHILE";
    const result = moveTask({
      workflow: workflowChanges,
      source: { task: sourceTask, crumbs: sourceTaskCrumbs },
      target: { crumbs: targetLocationCrumbs, task: targetTask },
      position,
    });
    const expectedResult = {
      name: "NewWorkflow_2qs7k",
      description: "",
      version: 1,
      tasks: [
        {
          name: "do_while",
          taskReferenceName: "do_while_ref",
          inputParameters: {},
          type: "DO_WHILE",
          startDelay: 0,
          optional: false,
          asyncComplete: false,
          loopCondition: "",
          evaluatorType: "value-param",
          loopOver: [
            {
              name: "simple",
              taskReferenceName: "simple_ref",
              type: "SIMPLE",
            },
          ],
        },
      ],
      inputParameters: [],
      outputParameters: {},
      schemaVersion: 2,
      restartable: true,
      workflowStatusListenerEnabled: false,
      ownerEmail: "najeeb.thangal@orkes.io",
      timeoutPolicy: "ALERT_ONLY",
      timeoutSeconds: 0,
      failureWorkflow: "",
    };
    expect(result).toEqual(expectedResult);
  });
});

describe("updateTaskReferenceName", () => {
  it("should update joinOn references in JOIN tasks", () => {
    const tasks = [
      {
        name: "fork",
        taskReferenceName: "fork_ref",
        inputParameters: {},
        type: "FORK_JOIN",
        defaultCase: [],
        forkTasks: [
          [
            {
              name: "http_poll",
              taskReferenceName: "http_poll_ref",
              type: "HTTP_POLL",
              inputParameters: {
                http_request: {
                  uri: "https://orkes-api-tester.orkesconductor.com/api",
                  method: "GET",
                  accept: "application/json",
                  contentType: "application/json",
                  terminationCondition:
                    "(function(){ return $.output.response.body.randomInt > 10;})();",
                  pollingInterval: "60",
                  pollingStrategy: "FIXED",
                  encode: true,
                },
              },
            },
          ],
          [
            {
              name: "event",
              taskReferenceName: "event_x4f_ref",
              type: "EVENT",
              sink: "sqs:internal_event_name",
              inputParameters: {},
            },
          ],
        ],
      },
      {
        name: "join",
        taskReferenceName: "join_ref",
        inputParameters: {},
        type: "JOIN",
        joinOn: ["event_x4f_ref", "http_poll_ref"],
        optional: false,
        asyncComplete: false,
      },
    ];

    const updated = updateTaskReferenceName(
      tasks,
      "http_poll_ref",
      "http_poll_ref_updated",
    );

    expect(updated[1].joinOn).toContain("http_poll_ref_updated");
    expect(updated[1].joinOn).not.toContain("http_poll_ref");
    expect(updated[0]).toEqual(tasks[0]);
  });

  it("should not update joinOn if oldRef is not present", () => {
    const tasks = [
      {
        name: "join",
        taskReferenceName: "join_ref",
        type: "JOIN",
        joinOn: ["ref2"],
        inputParameters: {},
      },
    ];

    const updated = updateTaskReferenceName(tasks, "ref1", "ref1_new");
    expect(updated[0].joinOn).toEqual(["ref2"]);
  });

  it("should not modify non-JOIN tasks", () => {
    const tasks = [
      {
        name: "simple",
        taskReferenceName: "ref1",
        type: "SIMPLE",
        inputParameters: {},
      },
    ];

    const updated = updateTaskReferenceName(tasks, "ref1", "ref1_new");
    expect(updated[0]).toEqual(tasks[0]);
  });

  it("should handle empty tasks array", () => {
    expect(updateTaskReferenceName([], "ref1", "ref1_new")).toEqual([]);
  });
});
