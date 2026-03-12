import { edgeMapper } from "./edgeMapper";

describe("edgeMapper", () => {
  const imageResizeTask = {
    name: "image_convert_resize_jim",
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
  };
  const uploadImageTask = {
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
  };
  const forkJoinTask = {
    name: "fork_join",
    taskReferenceName: "fork_ref",
    inputParameters: {},
    type: "FORK_JOIN",
    decisionCases: {},
    defaultCase: [],
    forkTasks: [[]],
    startDelay: 0,
    joinOn: [],
    optional: false,
    defaultExclusiveJoinTask: [],
    asyncComplete: false,
    loopOver: [],
  };
  const sampleJoinTask = {
    name: "join",
    taskReferenceName: "join_ref",
    inputParameters: {},
    type: "JOIN",
    decisionCases: {},
    defaultCase: [],
    forkTasks: [],
    startDelay: 0,
    joinOn: ["process_population_max_ref", "process_population_min_ref"],
    optional: false,
    defaultExclusiveJoinTask: [],
    asyncComplete: false,
    loopOver: [],
  };
  const terminateTask = {
    name: "terminate_due_to_bank_rejection",
    taskReferenceName: "terminate_due_to_bank_rejection",
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
  };

  const sampleSwitchTask = {
    name: "sample_task_name_switch_pm7wsj_ref",
    taskReferenceName: "sample_task_name_switch_pm7wsj_ref",
    inputParameters: {
      switchCaseValue: "",
    },
    type: "SWITCH",
    decisionCases: {
      new_case_ms0jy: [forkJoinTask, sampleJoinTask],
    },
    defaultCase: [],
    evaluatorType: "value-param",
    expression: "switchCaseValue",
  };
  const switchWithTerminate = {
    name: "sample_task_name_switch_pm7wsj_ref",
    taskReferenceName: "sample_task_name_switch_pm7wsj_ref",
    inputParameters: {
      switchCaseValue: "",
    },
    type: "SWITCH",
    decisionCases: {
      new_case_ms0jy: [forkJoinTask, sampleJoinTask],
    },
    defaultCase: [terminateTask],
    evaluatorType: "value-param",
    expression: "switchCaseValue",
  };

  it("Should create the joining edge between two generic tasks", () => {
    const edges = edgeMapper(imageResizeTask, uploadImageTask);
    expect(edges.length).toBe(1);
    expect(edges[0]).toEqual({
      id: "edge_upload_toS3_ref-image_convert_resize_ref",
      from: "upload_toS3_ref",
      fromPort: "upload_toS3_ref-south-port",
      toPort: "image_convert_resize_ref-to",
      to: "image_convert_resize_ref",

      data: {
        unreachableEdge: false,
      },
    });
  });

  it("Should return empty if there is no previous task", () => {
    const edges = edgeMapper(imageResizeTask, undefined);
    expect(edges.length).toBe(0);
  });

  it("Should return empty if currentTask is type join and previous task is fork join", () => {
    const edges = edgeMapper(sampleJoinTask, forkJoinTask);
    expect(edges.length).toBe(0);
  });

  it("Should return empty if currentTask is type FORK_JOIN_DYNAMIC and previous task is fork join", () => {
    const edges = edgeMapper(sampleJoinTask, {
      ...forkJoinTask,
      type: "FORK_JOIN_DYNAMIC",
    });
    expect(edges.length).toBe(0);
  });

  it("Should connect with tasks that TERMINATE", async () => {
    const edges = edgeMapper(sampleJoinTask, terminateTask, false);
    expect(edges.length).toBe(1);
  });

  it("Should return a single edge connected to current task if previous task is switch and readOnly is false", () => {
    const edges = edgeMapper(sampleJoinTask, sampleSwitchTask, true);
    expect(edges).toEqual([
      {
        id: "edge_sample_task_name_switch_pm7wsj_ref_switch_join-join_ref",
        from: "sample_task_name_switch_pm7wsj_ref_switch_join", // Switch join connection
        fromPort:
          "switch_fake_task_sample_task_name_switch_pm7wsj_ref_switch_join-south-port",
        toPort: "join_ref-to",
        to: "join_ref",

        data: {
          unreachableEdge: false,
        },
      },
    ]);
  });
  it("Should return an edge flagged with unreeachable if last task was a terminate task", () => {
    const edges = edgeMapper(sampleJoinTask, switchWithTerminate, false);
    expect(edges).toEqual([
      {
        id: "edge_sample_task_name_switch_pm7wsj_ref_switch_join-join_ref",
        from: "sample_task_name_switch_pm7wsj_ref_switch_join", // Switch join connection
        fromPort:
          "switch_fake_task_sample_task_name_switch_pm7wsj_ref_switch_join-south-port",
        toPort: "join_ref-to",
        to: "join_ref",
        data: {
          unreachableEdge: true,
        },
      },
    ]);
  });
  it("Should include the status of the edge if executionData is present. and both previous task and current task is complete", () => {
    const edges = edgeMapper(
      { ...imageResizeTask, executionData: { status: "COMPLETED" } },
      { ...uploadImageTask, executionData: { status: "COMPLETED" } },
      true,
    );
    expect(edges).toEqual([
      {
        id: "edge_upload_toS3_ref-image_convert_resize_ref",
        from: "upload_toS3_ref", // Switch join connection
        fromPort: "upload_toS3_ref-south-port",
        toPort: "image_convert_resize_ref-to",
        to: "image_convert_resize_ref",
        data: {
          status: "COMPLETED",
          unreachableEdge: false,
        },
      },
    ]);
  });
});
