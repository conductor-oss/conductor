import _dropRight from "lodash/dropRight";
import _first from "lodash/first";
import _last from "lodash/last";
import _merge from "lodash/merge";
import _update from "lodash/update";
import {
  decisionExecutionDataWithValidCase,
  lonleySwitchTask,
  switchExecutionDefaultByEvaluationResultNull,
  switchTasksWithTerminationNodes,
  switchTaskWithADecisionButNoTerminateTasks,
  unConnectedSwitchTask,
} from "../../../../../testData/diagramTests";
import { extractTaskReferenceName, tasksAsNodes } from "./core";
import {
  createFakeNode,
  decisionBranchesToNodesEdgesByCase,
  drillForEndTasks,
  processSwitchTasks,
  switchFakeTaskEdges,
  switchTaskToDecisionsToProcess,
  switchTaskToFakeNodeId,
  switchTaskToNode,
  taskToSwitchNodesEdges,
} from "./switch";

const isNonSwitchPred = ({ type }) => type !== "SWITCH";
const filterNonSwitch = (arr) => arr.filter(isNonSwitchPred);

describe("taskToSwitchNodesEdges", () => {
  it("Should return a switch task node with only connections to the pseudo task since the switch has not decisions", async () => {
    const switchTaskNode = await taskToSwitchNodesEdges(
      unConnectedSwitchTask,
      [],
      tasksAsNodes,
    );
    expect(switchTaskNode.nodes.length).toBe(2); // switch task and pseudo task
    expect(switchTaskNode.edges.length).toBe(1); // connection of default case to pseudo task
    expect(switchTaskNode.everyTaskIsTerminate).toBe(false);
  });
  it("Should return a node for switch a a node for pseudo switch a node for http and their connection edges. everyTaskIsTerminate should be false", async () => {
    const switchTaskNode = await taskToSwitchNodesEdges(
      switchTaskWithADecisionButNoTerminateTasks,
      [],
      tasksAsNodes,
    );
    expect(switchTaskNode.nodes.length).toBe(3); // switch task and pseudo task and http task

    expect(switchTaskNode.edges).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          from: "sample_task_name_h64r7_ref",
          to: "sample_task_name_yioskj_ref",
          text: "some_case",
        }),
        expect.objectContaining({
          from: "sample_task_name_yioskj_ref",
          to: "sample_task_name_h64r7_ref_switch_join",
        }),
        expect.objectContaining({
          from: "sample_task_name_h64r7_ref",
          to: "sample_task_name_h64r7_ref_switch_join",
          text: "defaultCase",
        }),
      ]),
    );
    // expect(switchTaskNode.edges.length).toBe(2); // connection of default case to pseudo task
    expect(switchTaskNode.everyTaskIsTerminate).toBe(false);
  });
  it("Should connect to Terminate task and return everyTaskIsTerminate false. since defaultCase is empty", async () => {
    const switchTaskOneTerminate = {
      name: "switch_task_jgkrgj",
      taskReferenceName: "switch_task_jgkrgj_ref",
      inputParameters: {
        switchCaseValue: "",
      },
      type: "SWITCH",
      decisionCases: {
        new_case_pdiat: [
          {
            name: "terminate_task_fhezy",
            taskReferenceName: "terminate_task_fhezy_ref",
            inputParameters: {
              terminationStatus: "COMPLETED",
            },
            type: "TERMINATE",
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
      },
    };
    const switchTaskNode = await taskToSwitchNodesEdges(
      switchTaskOneTerminate,
      [],
      tasksAsNodes,
    );
    expect(switchTaskNode.nodes.length).toBe(3); // switch task and pseudo task and http task
    expect(switchTaskNode.edges.length).toBe(3); // connection of default case to pseudo task. Terminate task now connects so 3
    expect(switchTaskNode.everyTaskIsTerminate).toBe(false);
  });
  it("Should connect to Terminate task and return everyTaskIsTerminate true. if every task ends in terminate", async () => {
    const switchTaskOneTerminate = {
      name: "switch_task_jgkrgj",
      taskReferenceName: "switch_task_jgkrgj_ref",
      inputParameters: {
        switchCaseValue: "",
      },
      type: "SWITCH",
      defaultCase: [
        {
          name: "other_name",
          taskReferenceName: "other_task_reference",
          inputParameters: {
            terminationStatus: "COMPLETED",
          },
          type: "TERMINATE",
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
      decisionCases: {
        new_case_pdiat: [
          {
            name: "terminate_task_fhezy",
            taskReferenceName: "terminate_task_fhezy_ref",
            inputParameters: {
              terminationStatus: "COMPLETED",
            },
            type: "TERMINATE",
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
      },
    };
    const switchTaskNode = await taskToSwitchNodesEdges(
      switchTaskOneTerminate,
      [],
      tasksAsNodes,
    );
    expect(switchTaskNode.nodes.length).toBe(4); // switch task and both terminates, We are now connecting to terminate
    expect(switchTaskNode.edges.length).toBe(4); // one connection for each terminate from switch
    expect(switchTaskNode.everyTaskIsTerminate).toBe(true);
  });
});

describe("switchTaskToNode", () => {
  const decisionKeys = [
    "emptyCase",
    "education",
    "property",
    "business",
    "defaultCase",
  ];
  const switchTaskNode = switchTaskToNode(lonleySwitchTask, [], decisionKeys);
  const fakeNodeForSwitch = createFakeNode(lonleySwitchTask, [], decisionKeys);
  it("Should return a switch task with south ports and index specified", () => {
    expect(switchTaskNode.id).toEqual(lonleySwitchTask.taskReferenceName);
    expect(switchTaskNode.data.task).toEqual(lonleySwitchTask);
    expect(switchTaskNode.ports).toEqual([
      {
        id: "loan_type_[key=emptyCase]-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
        index: 0,
      },
      {
        id: "loan_type_[key=education]-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
        index: 1,
      },
      {
        id: "loan_type_[key=property]-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
        index: 2,
      },
      {
        id: "loan_type_[key=business]-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
        index: 3,
      },
      {
        id: "loan_type_[key=defaultCase]-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
        index: 4,
      },
    ]);
    expect(fakeNodeForSwitch.ports).toEqual([
      {
        id: "switch_fake_task_loan_type_switch_join-south-port",
        side: "SOUTH",
        disabled: true,
        width: 2,
        height: 2,
      },
      {
        id: "loan_type_switch_join-to-join-to=[key=emptyCase]-north-port",
        width: 2,
        height: 2,
        side: "NORTH",
        disabled: true,
        hidden: true,
        index: 4,
      },
      {
        id: "loan_type_switch_join-to-join-to=[key=education]-north-port",
        width: 2,
        height: 2,
        side: "NORTH",
        disabled: true,
        hidden: true,
        index: 3,
      },
      {
        id: "loan_type_switch_join-to-join-to=[key=property]-north-port",
        width: 2,
        height: 2,
        side: "NORTH",
        disabled: true,
        hidden: true,
        index: 2,
      },
      {
        id: "loan_type_switch_join-to-join-to=[key=business]-north-port",
        width: 2,
        height: 2,
        side: "NORTH",
        disabled: true,
        hidden: true,
        index: 1,
      },
      {
        id: "loan_type_switch_join-to-join-to=[key=defaultCase]-north-port",
        width: 2,
        height: 2,
        side: "NORTH",
        disabled: true,
        hidden: true,
        index: 0,
      },
    ]);
  });
});

describe("drillForEndTasks", () => {
  it("Should return an empty array, if switch branches end in a Terminal task TERMINATE or TERMINAL", async () => {
    const unterminatedTasksConf = drillForEndTasks(tasksAsNodes);
    const unterminatedTasks = await unterminatedTasksConf(
      switchTasksWithTerminationNodes,
    );

    expect(filterNonSwitch(unterminatedTasks).length).toBe(0);
  });
  it("Should return the task that was not terminated", async () => {
    const switchTaskWithUnterminatedBranch = _update(
      { ...switchTasksWithTerminationNodes },
      "decisionCases.education",
      _dropRight,
    );
    const unterminatedTasksConf = drillForEndTasks(tasksAsNodes);
    const unterminatedTasks = await unterminatedTasksConf(
      switchTaskWithUnterminatedBranch,
    );
    expect(filterNonSwitch(unterminatedTasks).length).toBe(1);
    expect(_first(filterNonSwitch(unterminatedTasks)).name).toBe(
      "education_details",
    );
  });
  it("Should return no task if all final tasks are terminated", async () => {
    const switchTaskWithUnterminatedBranch = _update(
      { ...switchTasksWithTerminationNodes },
      "decisionCases.education",
      (arr) => {
        return _dropRight(arr).concat({
          name: "finalcondition",
          taskReferenceName: "finalCase",
          inputParameters: {
            finalCase: "${workflow.input.finalCase}",
          },
          type: "SWITCH",
          decisionCases: {
            notify: [
              {
                name: "integration_task_4",
                taskReferenceName: "integration_task_4",
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
              {
                name: "terminate",
                taskReferenceName: "terminate0",
                inputParameters: {
                  terminationStatus: "COMPLETED",
                  workflowOutput: { result: "${task0.output}" },
                },
                type: "TERMINATE",
                startDelay: 0,
                optional: false,
              },
            ],
          },
          defaultCase: [],
          forkTasks: [],
          startDelay: 0,
          joinOn: [],
          optional: false,
          defaultExclusiveJoinTask: [],
          asyncComplete: false,
          loopOver: [],
          evaluatorType: "value-param",
          expression: "finalCase",
        });
      },
    );

    const unterminatedTasksConf = drillForEndTasks(tasksAsNodes);
    const unterminatedTasks = await unterminatedTasksConf(
      switchTaskWithUnterminatedBranch,
    );
    expect(filterNonSwitch(unterminatedTasks).length).toBe(0);
  });

  it("Should return empty if nested Switch has terminated tasks", async () => {
    const switchTaskWithUnterminatedBranch = _update(
      { ...switchTasksWithTerminationNodes },
      "decisionCases.education",
      (arr) => {
        return _dropRight(arr).concat({
          name: "finalcondition",
          taskReferenceName: "finalCase",
          inputParameters: {
            finalCase: "${workflow.input.finalCase}",
          },
          type: "SWITCH",
          decisionCases: {
            notify: [
              {
                name: "integration_task_4",
                taskReferenceName: "integration_task_4",
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
            ],
          },
          defaultCase: [],
          forkTasks: [],
          startDelay: 0,
          joinOn: [],
          optional: false,
          defaultExclusiveJoinTask: [],
          asyncComplete: false,
          loopOver: [],
          evaluatorType: "value-param",
          expression: "finalCase",
        });
      },
    );

    const unterminatedTasksConf = drillForEndTasks(tasksAsNodes);
    const unterminatedTasks = await unterminatedTasksConf(
      switchTaskWithUnterminatedBranch,
    );
    expect(filterNonSwitch(unterminatedTasks).length).toBe(1);
    expect(_first(filterNonSwitch(unterminatedTasks)).name).toBe(
      "integration_task_4",
    );
  });
});

describe("Switch", () => {
  describe("switchTaskToDecisionsToProcess", () => {
    it("Should return available tasks to process defaultPath", () => {
      const swithTaskNoDefaults = { ...lonleySwitchTask, defaultCase: [] };
      const decisionBranches =
        switchTaskToDecisionsToProcess(swithTaskNoDefaults);
      const decisionKeys = Object.keys(decisionBranches);
      expect(decisionKeys).toEqual(
        expect.arrayContaining([
          "education",
          "property",
          "business",
          "defaultCase",
        ]),
      );
      const lastKey = decisionKeys[decisionKeys.length - 1];
      expect(lastKey).toBe("defaultCase");
    });
    it("Should return available tasks to process defaultPath where default case is equal to cas", () => {
      const decisionBranches = switchTaskToDecisionsToProcess(lonleySwitchTask);
      const decisionKeys = Object.keys(decisionBranches);
      expect(decisionKeys).toEqual(
        expect.arrayContaining([
          "education",
          "property",
          "business",
          "defaultCase",
        ]),
      );
    });
    it("Should return available tasks including defaultPath when default is not equal to an existing path", () => {
      const swithTaskNoDefaults = {
        ...lonleySwitchTask,
        defaultCase: [
          {
            name: "task_10",
            taskReferenceName: "task_10_last",
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
        ],
      };
      const decisionBranches =
        switchTaskToDecisionsToProcess(swithTaskNoDefaults);
      const decisionKeys = Object.keys(decisionBranches);
      expect(decisionKeys).toEqual(
        expect.arrayContaining([
          "education",
          "property",
          "business",
          "defaultCase",
        ]),
      );
    });
  });
  describe("decisionBranchesToNodesEdgesByCase", () => {
    it("Should return tasks and edges for available decissions", async () => {
      const educationExpectedId = extractTaskReferenceName(
        lonleySwitchTask.decisionCases.education,
      );
      const businessExpectedId = extractTaskReferenceName(
        lonleySwitchTask.decisionCases.business,
      );
      const propertyExpectedId = extractTaskReferenceName(
        lonleySwitchTask.decisionCases.property,
      );

      const defaultCaseExpectedId = extractTaskReferenceName(
        lonleySwitchTask.defaultCase,
      );

      const result = await decisionBranchesToNodesEdgesByCase(
        lonleySwitchTask,
        [],
        tasksAsNodes,
      );
      const extractNodeId = (name) => result[name].nodes.map(({ id }) => id);
      const educationNodeNames = extractNodeId("education");
      const businessNodeNames = extractNodeId("business");
      const propertiesNodeNames = extractNodeId("property");
      const defaultCaseNodeNames = extractNodeId("defaultCase");

      expect(educationNodeNames).toEqual(
        expect.arrayContaining(educationExpectedId),
      );
      expect(businessNodeNames).toEqual(
        expect.arrayContaining(businessExpectedId),
      );
      expect(propertiesNodeNames).toEqual(
        expect.arrayContaining(propertyExpectedId),
      );
      expect(propertyExpectedId).toEqual(
        expect.arrayContaining(propertyExpectedId),
      );
      expect(defaultCaseNodeNames).toEqual(
        expect.arrayContaining(defaultCaseExpectedId),
      );
    });
  });
  describe("processSwitchTasks", () => {
    it("Should return al nodes in every single case, last node in every branch. undefined if the branch is empty. and decisionKey, order should be in the same order as the nodes", async () => {
      const educationExpectedId = extractTaskReferenceName(
        lonleySwitchTask.decisionCases.education,
      );
      const businessExpectedId = extractTaskReferenceName(
        lonleySwitchTask.decisionCases.business,
      );
      const propertyExpectedId = extractTaskReferenceName(
        lonleySwitchTask.decisionCases.property,
      );

      const defaultCaseExpectedId = extractTaskReferenceName(
        lonleySwitchTask.defaultCase,
      );

      const { nodes, lastSwitchTasks, decisionKeys, lastSwitchNodes } =
        await processSwitchTasks(lonleySwitchTask, [], tasksAsNodes);

      const nodesName = nodes.map(({ id }) => id);
      // Every Node is covered
      expect(nodesName).toEqual(
        expect.arrayContaining(
          educationExpectedId
            .concat(businessExpectedId)
            .concat(propertyExpectedId)
            .concat(defaultCaseExpectedId),
        ),
      );

      // Every last task is there
      expect(lastSwitchTasks.map((task) => task?.taskReferenceName)).toEqual(
        expect.arrayContaining([
          _last(lonleySwitchTask.decisionCases.education).taskReferenceName,
          _last(lonleySwitchTask.decisionCases.business).taskReferenceName,
          _last(lonleySwitchTask.decisionCases.property).taskReferenceName,
          _last(lonleySwitchTask.defaultCase).taskReferenceName,
        ]),
      );

      // The empty path should be shown as undefined
      expect(lastSwitchNodes.some((a) => a === undefined)).toBe(true);

      //Every possible branch is there
      expect(decisionKeys).toEqual(
        expect.arrayContaining(
          Object.keys(lonleySwitchTask.decisionCases).concat("defaultCase"),
        ),
      );

      const undefinedNodeIdx = lastSwitchNodes.findIndex(
        (a) => a === undefined,
      );
      const emptyBranchDecsionIdx = decisionKeys.findIndex(
        (k) => k === "emptyCase",
      );
      // order of undefined node is the same as the emptyCase
      expect(undefinedNodeIdx).not.toBe(-1);
      expect(undefinedNodeIdx).toEqual(emptyBranchDecsionIdx);
    });
  });
});

describe("createFakeNode", () => {
  const decisionCasesKeys = Object.keys(lonleySwitchTask.decisionCases).concat(
    "defaultCase",
  );
  const result = createFakeNode(lonleySwitchTask, [], decisionCasesKeys);
  it("Should generate a fake node. with as many north ports as cases there is. and one SOUTH port", () => {
    const northPorts = result.ports.filter(({ side }) => side === "NORTH");
    expect(northPorts.length).toBe(decisionCasesKeys.length);
    // Should have the same order
    expect(
      decisionCasesKeys.every(
        (k, idx) =>
          northPorts[idx].id ===
          `${switchTaskToFakeNodeId(
            lonleySwitchTask,
          )}-to-join-to=[key=${k}]-north-port`,
      ),
    ).toBeTruthy();
  });
});

describe("switchFakeTaskEdges", () => {
  const switchCreatedNode = {
    id: "pin_validation",
    text: "pin_validation",
    ports: [
      {
        id: "pin_validation_[key=]-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
        index: 0,
      },
      {
        id: "pin_validation_[key=CASE-2]-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
        index: 1,
      },
      {
        id: "pin_validation_[key=CASE-1]-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
        index: 2,
      },
      {
        id: "pin_validation_[key=defaultCase]-south-port",
        width: 2,
        height: 2,
        side: "SOUTH",
        disabled: true,
        index: 3,
      },
    ],
    data: {
      task: {
        name: "pin_validation",
        taskReferenceName: "pin_validation",
        inputParameters: {
          case: "${workflow.input.case}",
        },
        type: "SWITCH",
        decisionCases: {
          "": [],
          "CASE-2": [],
          "CASE-1": [],
        },
        defaultCase: [],
        forkTasks: [],
        startDelay: 0,
        joinOn: [],
        optional: false,
        rateLimited: false,
        defaultExclusiveJoinTask: [],
        asyncComplete: false,
        loopOver: [],
        evaluatorType: "graaljs",
        expression: "((\n  function () {\n    return $.case;\n  }\n))();",
        onStateChange: {},
        executionData: {
          status: "COMPLETED",
          executed: true,
          attempts: 1,
          outputData: {
            evaluationResult: ["null"],
            selectedCase: "null",
          },
        },
      },
      crumbs: [
        {
          parent: null,
          ref: "pin_validation",
          refIdx: 0,
          type: "SWITCH",
        },
      ],
      status: "COMPLETED",
      executed: true,
      attempts: 1,
      outputData: {
        evaluationResult: ["null"],
        selectedCase: "null",
      },
    },
    width: 450,
    height: 200,
  };
  const fakeNode = {
    id: "pin_validation_switch_join",
    data: {
      task: {
        name: "pin_validation",
        taskReferenceName: "pin_validation",
        inputParameters: {
          case: "${workflow.input.case}",
        },
        type: "SWITCH_JOIN",
        decisionCases: {
          "": [],
          "CASE-2": [],
          "CASE-1": [],
        },
        defaultCase: [],
        forkTasks: [],
        startDelay: 0,
        joinOn: [],
        optional: false,
        rateLimited: false,
        defaultExclusiveJoinTask: [],
        asyncComplete: false,
        loopOver: [],
        evaluatorType: "graaljs",
        expression: "((\n  function () {\n    return $.case;\n  }\n))();",
        onStateChange: {},
        executionData: {
          status: "COMPLETED",
          executed: true,
          attempts: 1,
          outputData: {
            evaluationResult: ["null"],
            selectedCase: "null",
          },
        },
      },
      crumbs: [
        {
          parent: null,
          ref: "pin_validation",
          refIdx: 0,
          type: "SWITCH",
        },
      ],
      originalTask: {
        name: "pin_validation",
        taskReferenceName: "pin_validation",
        inputParameters: {
          case: "${workflow.input.case}",
        },
        type: "SWITCH",
        decisionCases: {
          "": [],
          "CASE-2": [],
          "CASE-1": [],
        },
        defaultCase: [],
        forkTasks: [],
        startDelay: 0,
        joinOn: [],
        optional: false,
        rateLimited: false,
        defaultExclusiveJoinTask: [],
        asyncComplete: false,
        loopOver: [],
        evaluatorType: "graaljs",
        expression: "((\n  function () {\n    return $.case;\n  }\n))();",
        onStateChange: {},
        executionData: {
          status: "COMPLETED",
          executed: true,
          attempts: 1,
          outputData: {
            evaluationResult: ["null"],
            selectedCase: "null",
          },
        },
      },
    },
    ports: [
      {
        id: "switch_fake_task_pin_validation_switch_join-south-port",
        side: "SOUTH",
        disabled: true,
        width: 2,
        height: 2,
      },
      {
        id: "pin_validation_switch_join-to-join-to=[key=]-north-port",
        width: 2,
        height: 2,
        side: "NORTH",
        disabled: true,
        hidden: true,
        index: 3,
      },
      {
        id: "pin_validation_switch_join-to-join-to=[key=CASE-2]-north-port",
        width: 2,
        height: 2,
        side: "NORTH",
        disabled: true,
        hidden: true,
        index: 2,
      },
      {
        id: "pin_validation_switch_join-to-join-to=[key=CASE-1]-north-port",
        width: 2,
        height: 2,
        side: "NORTH",
        disabled: true,
        hidden: true,
        index: 1,
      },
      {
        id: "pin_validation_switch_join-to-join-to=[key=defaultCase]-north-port",
        width: 2,
        height: 2,
        side: "NORTH",
        disabled: true,
        hidden: true,
        index: 0,
      },
    ],
    text: "pin_validation_switch_join",
    width: 350,
    height: 55,
  };

  it("Should paint the defaultCase as green (COMPLETED) - when empty decision case is present and selectedCase is null", () => {
    const decisionKeys = ["", "CASE-2", "CASE-1", "defaultCase"];
    const result = switchFakeTaskEdges(
      [undefined, undefined, undefined, undefined],
      [undefined, undefined, undefined, undefined],
      switchCreatedNode,
      decisionKeys,
      fakeNode,
      null,
    );

    const result1 = [
      {
        id: "edge_dp__fake_pin_validation_-direct",
        from: "pin_validation",
        fromPort: "pin_validation_[key=]-south-port",
        toPort: "pin_validation_switch_join-to-join-to=[key=]-north-port",
        to: "pin_validation_switch_join",
        text: "",
      },
      {
        id: "edge_dp__fake_pin_validation_CASE-2-direct",
        from: "pin_validation",
        fromPort: "pin_validation_[key=CASE-2]-south-port",
        toPort: "pin_validation_switch_join-to-join-to=[key=CASE-2]-north-port",
        to: "pin_validation_switch_join",
        text: "CASE-2",
      },
      {
        id: "edge_dp__fake_pin_validation_CASE-1-direct",
        from: "pin_validation",
        fromPort: "pin_validation_[key=CASE-1]-south-port",
        toPort: "pin_validation_switch_join-to-join-to=[key=CASE-1]-north-port",
        to: "pin_validation_switch_join",
        text: "CASE-1",
      },
      {
        id: "edge_dp__fake_pin_validation_defaultCase-direct",
        from: "pin_validation",
        fromPort: "pin_validation_[key=defaultCase]-south-port",
        toPort:
          "pin_validation_switch_join-to-join-to=[key=defaultCase]-north-port",
        to: "pin_validation_switch_join",
        text: "defaultCase",
        data: { status: "COMPLETED" },
      },
    ];
    expect(result).toEqual(result1);
  });
  it("Should paint green default if selected case is not in the decision keys", async () => {
    const switchTaskNode = await taskToSwitchNodesEdges(
      switchExecutionDefaultByEvaluationResultNull,
      [],
      tasksAsNodes,
    );
    expect(switchTaskNode.edges).toEqual([
      {
        id: "edge_pin_validation-http_ref",
        from: "pin_validation",
        to: "http_ref",
        fromPort: "pin_validation_[key=defaultCase]-south-port",
        toPort: "pin_validation_[key=defaultCase]-south-port-to",
        text: "defaultCase",
        data: {
          status: "COMPLETED",
          unreachableEdge: false,
          action: "ADD_TASK_ABOVE",
        },
      },
      {
        id: "edge_dp__fake_pin_validation_-direct",
        from: "pin_validation",
        fromPort: "pin_validation_[key=]-south-port",
        toPort: "pin_validation_switch_join-to-join-to=[key=]-north-port",
        to: "pin_validation_switch_join",
        text: "",
      },
      {
        id: "edge_dp__fake_pin_validation_CASE-2-direct",
        from: "pin_validation",
        fromPort: "pin_validation_[key=CASE-2]-south-port",
        toPort: "pin_validation_switch_join-to-join-to=[key=CASE-2]-north-port",
        to: "pin_validation_switch_join",
        text: "CASE-2",
      },
      {
        id: "edge_dp__fake_pin_validation_CASE-1-direct",
        from: "pin_validation",
        fromPort: "pin_validation_[key=CASE-1]-south-port",
        toPort: "pin_validation_switch_join-to-join-to=[key=CASE-1]-north-port",
        to: "pin_validation_switch_join",
        text: "CASE-1",
      },
      {
        id: "edge_dp__fake_pin_validation_http_ref",
        from: "http_ref",
        to: "pin_validation_switch_join",
        toPort:
          "pin_validation_switch_join-to-join-to=[key=defaultCase]-north-port",
        fromPort: "http_ref-south-port",
        data: {
          status: "COMPLETED",
        },
      },
    ]); // switch task and pseudo task and http task

    // Test DECISION task with empty caseOutput
    const decisionTaskWithEmptyCase = {
      ...decisionExecutionDataWithValidCase,
      type: "DECISION",
      executionData: {
        status: "COMPLETED",
        executed: true,
        attempts: 1,
        outputData: {
          evaluationResult: [],
          caseOutput: "",
        },
      },
    };

    const decisionTaskNodeEmptyCase = await taskToSwitchNodesEdges(
      decisionTaskWithEmptyCase,
      [],
      tasksAsNodes,
    );

    // Verify defaultCase is marked as completed when caseOutput is empty
    const decisionDefaultCaseEdge = decisionTaskNodeEmptyCase.edges.find(
      (edge) => edge.text === "defaultCase",
    );
    expect(decisionDefaultCaseEdge?.data?.status).toBe("COMPLETED");

    // Test DECISION task with invalid caseOutput
    const decisionTaskWithInvalidCase = {
      ...decisionExecutionDataWithValidCase,
      type: "DECISION",
      executionData: {
        status: "COMPLETED",
        executed: true,
        attempts: 1,
        outputData: {
          evaluationResult: ["INVALID_CASE"],
          caseOutput: "INVALID_CASE",
        },
      },
    };

    const decisionTaskNodeInvalidCase = await taskToSwitchNodesEdges(
      decisionTaskWithInvalidCase,
      [],
      tasksAsNodes,
    );

    // Verify defaultCase is marked as completed when caseOutput is not in decision keys
    const decisionDefaultCaseEdgeInvalid =
      decisionTaskNodeInvalidCase.edges.find(
        (edge) => edge.text === "defaultCase",
      );
    expect(decisionDefaultCaseEdgeInvalid?.data?.status).toBe("COMPLETED");

    // Test DECISION task with valid caseOutput matching a decision key
    const decisionTaskWithValidCase = {
      ...decisionExecutionDataWithValidCase,
      type: "DECISION",
      executionData: {
        status: "COMPLETED",
        executed: true,
        attempts: 1,
        outputData: {
          evaluationResult: ["MEDIUM"],
          caseOutput: "MEDIUM",
        },
      },
    };

    const decisionTaskNodeValidCase = await taskToSwitchNodesEdges(
      decisionTaskWithValidCase,
      [],
      tasksAsNodes,
    );

    // Verify the matching case edge is marked as completed
    const decisionCase1Edge = decisionTaskNodeValidCase.edges.find(
      (edge) => edge.text === "MEDIUM",
    );
    expect(decisionCase1Edge?.data?.status).toBe("COMPLETED");

    // Verify defaultCase is NOT marked as completed when a valid case is selected
    const decisionDefaultCaseEdgeValid = decisionTaskNodeValidCase.edges.find(
      (edge) => edge.text === "defaultCase",
    );
    expect(decisionDefaultCaseEdgeValid?.data?.status).toBeUndefined();

    const switchTaskNodeCase1 = await taskToSwitchNodesEdges(
      _merge({}, switchExecutionDefaultByEvaluationResultNull, {
        executionData: {
          status: "COMPLETED",
          executed: true,
          attempts: 1,
          outputData: {
            evaluationResult: ["CASE-1"],
            selectedCase: "CASE-1",
          },
        },
      }),
      [],
      tasksAsNodes,
    );
    expect(switchTaskNodeCase1.edges).toEqual([
      {
        id: "edge_pin_validation-http_ref",
        from: "pin_validation",
        to: "http_ref",
        fromPort: "pin_validation_[key=defaultCase]-south-port",
        toPort: "pin_validation_[key=defaultCase]-south-port-to",
        text: "defaultCase",
        data: {
          action: "ADD_TASK_ABOVE",
        },
      },
      {
        id: "edge_dp__fake_pin_validation_-direct",
        from: "pin_validation",
        fromPort: "pin_validation_[key=]-south-port",
        toPort: "pin_validation_switch_join-to-join-to=[key=]-north-port",
        to: "pin_validation_switch_join",
        text: "",
      },
      {
        id: "edge_dp__fake_pin_validation_CASE-2-direct",
        from: "pin_validation",
        fromPort: "pin_validation_[key=CASE-2]-south-port",
        toPort: "pin_validation_switch_join-to-join-to=[key=CASE-2]-north-port",
        to: "pin_validation_switch_join",
        text: "CASE-2",
      },
      {
        id: "edge_dp__fake_pin_validation_CASE-1-direct",
        from: "pin_validation",
        fromPort: "pin_validation_[key=CASE-1]-south-port",
        toPort: "pin_validation_switch_join-to-join-to=[key=CASE-1]-north-port",
        to: "pin_validation_switch_join",
        text: "CASE-1",
        data: {
          status: "COMPLETED",
        },
      },
      {
        id: "edge_dp__fake_pin_validation_http_ref",
        from: "http_ref",
        to: "pin_validation_switch_join",
        toPort:
          "pin_validation_switch_join-to-join-to=[key=defaultCase]-north-port",
        fromPort: "http_ref-south-port",
        data: {
          status: "COMPLETED",
        },
      },
    ]);
  });
  it("should mark edge as unreachable when connecting from TERMINATE task", async () => {
    const switchWithTerminate = {
      name: "switch_with_terminate",
      taskReferenceName: "switch_terminate_ref",
      type: "SWITCH",
      decisionCases: {
        case1: [
          {
            name: "terminate_task",
            taskReferenceName: "terminate_ref",
            type: "TERMINATE",
            inputParameters: {
              terminationStatus: "COMPLETED",
            },
          },
        ],
      },
      defaultCase: [],
      evaluatorType: "value-param",
      expression: "switchCaseValue",
    };

    const { edges } = await taskToSwitchNodesEdges(
      switchWithTerminate,
      [],
      async (tasks) => ({
        nodes: tasks.map((task) => ({
          id: task.taskReferenceName,
          data: { task },
          ports: [],
        })),
        edges: [],
        previousTask: tasks[tasks.length - 1],
        previousTaskAllowsConnection: false,
      }),
    );

    const terminateEdge = edges.find(
      (edge) =>
        edge.from === "terminate_ref" &&
        edge.to === "switch_terminate_ref_switch_join",
    );

    expect(terminateEdge).toEqual(
      expect.objectContaining({
        id: "edge_dp__fake_switch_terminate_ref_terminate_ref",
        from: "terminate_ref",
        to: "switch_terminate_ref_switch_join",
        toPort:
          "switch_terminate_ref_switch_join-to-join-to=[key=case1]-north-port",
        data: { unreachableEdge: true },
      }),
    );
  });
});
