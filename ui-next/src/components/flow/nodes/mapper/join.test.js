import { joinEdgeForSwitch, joinTasksToNodesEdges } from "./join";

describe("toJoinTaskToNodesEdgesFn", () => {
  const joinTask = {
    name: "join_task_9ysua_ref",
    taskReferenceName: "join_task_9ysua_ref",
    inputParameters: {},
    type: "JOIN",
    decisionCases: {},
    defaultCase: [],
    forkTasks: [],
    startDelay: 0,
    joinOn: [],
    optional: false,
    defaultExclusiveJoinTask: [],
    asyncComplete: false,
    loopOver: [],
  };
  const anEventTask = {
    name: "event_task_q3cxy_ref",
    taskReferenceName: "event_task_q3cxy_ref",
    type: "EVENT",
    sink: "conductor:internal_event_name",
  };

  it("Should return a labeless edge since the task is after the switch", () => {
    const switchTask = {
      name: "switch_task_mjpgf_ref",
      taskReferenceName: "switch_task_mjpgf_ref",
      inputParameters: {
        switchCaseValue: "",
      },
      type: "SWITCH",
      decisionCases: {
        new_case_ceop1: [],
      },
      defaultCase: [],
      evaluatorType: "value-param",
      expression: "switchCaseValue",
    };
    const forkTask = {
      name: "fork_task_a3kx5_ref",
      taskReferenceName: "fork_task_a3kx5_ref",
      inputParameters: {},
      type: "FORK_JOIN",
      decisionCases: {},
      defaultCase: [],
      forkTasks: [[switchTask, anEventTask]],
      startDelay: 0,
      joinOn: [],
      optional: false,
      defaultExclusiveJoinTask: [],
      asyncComplete: false,
      loopOver: [],
    };
    const { nodes, edges } = joinTasksToNodesEdges(joinTask, forkTask, [], []);
    const joinNode = nodes[0];
    expect(joinNode.id).toBe(joinTask.taskReferenceName);
    expect(edges.length).toBe(1);
    expect(edges[0].from).toBe(anEventTask.taskReferenceName);
    expect(edges[0].to).toBe(joinTask.taskReferenceName);
  });
  it("Should add an unreachable task as an edge if the fork tasks array is empty", () => {
    const forkTask = {
      name: "fork_task_192fs",
      taskReferenceName: "fork_task_192fs_ref",
      inputParameters: {},
      type: "FORK_JOIN",
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
    };
    const joinTask = {
      name: "join_task_nc6vo",
      taskReferenceName: "join_task_nc6vo_ref",
      inputParameters: {},
      type: "JOIN",
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
    };
    const { nodes, edges } = joinTasksToNodesEdges(joinTask, forkTask, [], []);
    expect(nodes.length).toBe(1);
    expect(edges.length).toBe(1);
    expect(edges[0].from).toBe("fork_task_192fs_ref");
    expect(edges[0].to).toBe("join_task_nc6vo_ref");
    expect(edges[0].data.unreachableEdge).toBe(true);
  });
  it("Should mark edge as delayed when task is not in joinOn", () => {
    const switchTask = {
      name: "switch_task",
      taskReferenceName: "switch_task_ref",
      type: "SWITCH",
      defaultCase: [],
      startDelay: 0,
      optional: false,
      asyncComplete: false,
    };

    const joinTask = {
      name: "join_task",
      taskReferenceName: "join_task_ref",
      type: "JOIN",
      joinOn: [], // Empty joinOn means switch task not included
      startDelay: 0,
      optional: false,
      asyncComplete: false,
    };

    const result = joinEdgeForSwitch(switchTask, 0, joinTask);

    expect(result.joinOn.length).toBe(1);
    expect(result.joinOn[0].data.delayedEdge).toBe(true);
  });

  it("Should not mark edge as delayed when task is in joinOn", () => {
    const switchTask = {
      name: "switch_task",
      taskReferenceName: "switch_task_ref",
      type: "SWITCH",
      defaultCase: [],
      startDelay: 0,
      optional: false,
      asyncComplete: false,
    };

    const joinTask = {
      name: "join_task",
      taskReferenceName: "join_task_ref",
      type: "JOIN",
      joinOn: ["switch_task_ref"], // Switch task included in joinOn
      startDelay: 0,
      optional: false,
      asyncComplete: false,
    };

    const result = joinEdgeForSwitch(switchTask, 0, joinTask);

    expect(result.joinOn.length).toBe(1);
    expect(result.joinOn[0].data.delayedEdge).toBe(false);
  });
  it("Should mark edge as delayed in joinTasksToNodesEdges when task is not in joinOn", () => {
    const forkTask = {
      name: "fork_task",
      taskReferenceName: "fork_task_ref",
      type: "FORK_JOIN",
      forkTasks: [
        [
          {
            name: "inner_task",
            taskReferenceName: "inner_task_ref",
            type: "SIMPLE",
            startDelay: 0,
            optional: false,
            asyncComplete: false,
          },
        ],
      ],
      startDelay: 0,
      optional: false,
      asyncComplete: false,
    };

    const joinTask = {
      name: "join_task",
      taskReferenceName: "join_task_ref",
      type: "JOIN",
      joinOn: [], // Empty joinOn means inner task not included
      startDelay: 0,
      optional: false,
      asyncComplete: false,
    };

    const result = joinTasksToNodesEdges(joinTask, forkTask, [], []);

    expect(result.edges.length).toBe(1);
    expect(result.edges[0].data.delayedEdge).toBe(true);
  });

  it("Should not mark edge as delayed in joinTasksToNodesEdges when task is in joinOn", () => {
    const forkTask = {
      name: "fork_task",
      taskReferenceName: "fork_task_ref",
      type: "FORK_JOIN",
      forkTasks: [
        [
          {
            name: "inner_task",
            taskReferenceName: "inner_task_ref",
            type: "SIMPLE",
            startDelay: 0,
            optional: false,
            asyncComplete: false,
          },
        ],
      ],
      startDelay: 0,
      optional: false,
      asyncComplete: false,
    };

    const joinTask = {
      name: "join_task",
      taskReferenceName: "join_task_ref",
      type: "JOIN",
      joinOn: ["inner_task_ref"], // Inner task included in joinOn
      startDelay: 0,
      optional: false,
      asyncComplete: false,
    };

    const result = joinTasksToNodesEdges(joinTask, forkTask, [], []);

    expect(result.edges.length).toBe(1);
    expect(result.edges[0].data.delayedEdge).toBe(false);
  });
});
