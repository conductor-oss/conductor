export const oneLoopOneLevelDeep = {
  nodes: [
    {
      id: "__start",
      type: "default",
      data: {
        label: "__start",
      },
      position: {
        x: 0,
        y: 20,
      },
    },
    {
      id: "my_fork_join_ref",
      type: "default",
      data: {
        label: "my_fork_join_ref",
      },
      position: {
        x: 0,
        y: 20,
      },
    },
    {
      id: "loop_1",
      type: "default",
      data: {
        label: "loop_1",
      },
      style: {
        width: 410,
        height: 300,
      },
    },
    {
      id: "loop_1_task_iter",
      type: "default",
      data: {
        label: "loop_1_task_iter",
      },
      position: {
        x: 0,
        y: 0,
      },
      parentNode: "loop_1",
      extent: "parent",
    },
    {
      id: "loop_1_sv",
      type: "default",
      data: {
        label: "loop_1_sv",
      },
      position: {
        x: 0,
        y: 0,
      },
      parentNode: "loop_1",
      extent: "parent",
    },
    {
      id: "fork_join_ref",
      type: "default",
      data: {
        label: "fork_join_ref",
      },
      position: {
        x: 0,
        y: 20,
      },
    },
    {
      id: "__final",
      type: "default",
      data: {
        label: "__final",
      },
      position: {
        x: 0,
        y: 20,
      },
    },
  ],
  edges: [
    {
      id: "edge___start-my_fork_join_ref",
      source: "__start",
      target: "my_fork_join_ref",
      type: "smoothstep",
    },
    {
      id: "edge_my_fork_join_ref-loop_1",
      source: "my_fork_join_ref",
      target: "loop_1",
    },
    {
      id: "edge_loop_1_task_iter-loop_1_sv",
      source: "loop_1_task_iter",
      target: "loop_1_sv",
      type: "smoothstep",
      zIndex: 100,
    },
    {
      id: "edge_fork_join_ref-__final",
      source: "fork_join_ref",
      target: "__final",
      type: "smoothstep",
    },
    {
      id: "edge_jt_loop_1-fork_join_ref",
      source: "loop_1",
      target: "fork_join_ref",
    },
  ],
};
