import React, { Component } from "react";
import WorkflowDAG from "../../components/diagram/WorkflowDAG";
import WorkflowGraph from "../../components/diagram/WorkflowGraph";

const workflowDef = {
  tasks: [
    {
      name: "fork_join",
      taskReferenceName: "fork",
      type: "FORK_JOIN",
      forkTasks: [
        [
          {
            name: "forkChild",
            type: "SIMPLE",
            taskReferenceName: "forkChild_grp1a",
          },
          {
            name: "forkChild",
            type: "SIMPLE",
            taskReferenceName: "forkChild_grp1b",
          },
        ],
        [
          {
            name: "forkChild",
            type: "SIMPLE",
            taskReferenceName: "forkchild_grp2",
          },
        ],
        [
          {
            name: "forkChild",
            type: "SIMPLE",
            taskReferenceName: "forkchild_grp3",
          },
        ],
        [
          {
            name: "forkChild",
            type: "SIMPLE",
            taskReferenceName: "forkchild_grp4",
          },
        ],
      ],
    },
    {
      name: "join",
      taskReferenceName: "join",
      type: "JOIN",
      joinOn: ["forkChild_par1", "forkChild_par2", "forkChild_ser1"],
    },

    {
      name: "decision",
      taskReferenceName: "decision",
      type: "DECISION",
      decisionCases: [
        [
          {
            name: "simple_task",
            type: "SIMPLE",
            taskReferenceName: "completed",
          },
        ],
        [
          {
            name: "simple_task",
            type: "SIMPLE",
            taskReferenceName: "failed",
          },
        ],
      ],
    },
    {
      name: "exclusive_join",
      taskReferenceName: "exclusiveJoin",
      type: "EXCLUSIVE_JOIN",
      joinOn: ["completed", "failed"],
      defaultExclusiveJoinTask: ["completed"],
    },
    {
      name: "subworkflow",
      taskReferenceName: "subworkflow",
      type: "SUB_WORKFLOW",
      subworkflowParam: { name: "foo" },
    },
    {
      name: "dynamic_fork",
      taskReferenceName: "dynamic_fork",
      type: "FORK_JOIN_DYNAMIC",
      dynamicForkTasksParam: "dynamicTasks",
      dynamicForkTasksInputParamName: "dynamicTasksInput",
    },
    {
      name: "join",
      taskReferenceName: "dynamic_join",
      type: "JOIN",
    },
  ],
};

class Legend extends Component {
  constructor() {
    super();
    this.state = {
      dag: new WorkflowDAG(null, workflowDef),
    };
  }
  render() {
    const { dag } = this.state;
    return (
      <div style={{ display: "flex", flexDirection: "row" }}>
        <WorkflowGraph dag={dag} />
      </div>
    );
  }
}

export default Legend;
