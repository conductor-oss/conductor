import {
  executionTasksToStatusMap,
  taskStatusUpdater,
  relatedNamesToTaskDef,
  executionToWorkflowDef,
  doWhileSelectionForStatusMap,
} from "./executionMapper";
import {
  sampleExecution,
  sampleExecutionWithForkJoin,
  newDynamicForkSample,
  sampleExecutionDoWhile,
  sampleExecutionMultiDoWhile,
  sampleStatusMap,
  doWhileSelectionForStatusMapResultSingleDoWhile,
  doWhileSelectionForStatusMapResultMultiDowhile,
} from "./sampleExecutions";

describe("executionToWorkflowDef", () => {
  describe("executionTasksToStatusMap", () => {
    it("Should build a map will all execution tasks and selected task", () => {
      const sampleExecutedObject = {
        ref: "get_weather_ref",
        taskId: "5c61b913-5883-4725-8d39-0edd3029cbaf",
      };
      const builtMap = executionTasksToStatusMap(sampleExecution.tasks);
      expect(Object.keys(builtMap)).toEqual(
        expect.arrayContaining(["get_IP_ref", "get_weather_ref"]),
      );
      expect(builtMap[sampleExecutedObject.ref].taskId).toEqual(
        sampleExecutedObject.taskId,
      );
    });
  });
  describe("taskStatusUpdater", () => {
    it("Should return tasks with an executionData object", () => {
      const sampleMap = {
        get_IP_ref: { status: "COMPLETED", executed: true, loopOver: [] },
        get_weather_ref: { status: "FAILED", executed: false, loopOver: [] },
      };
      const sampleExecutionTasks = sampleExecution.workflowDefinition.tasks;
      const result = taskStatusUpdater(sampleExecutionTasks, sampleMap);
      expect(
        result.every(({ executionData }) => executionData != null),
      ).toEqual(true);
    });
  });
  describe("relatedNamesToTaskDef", () => {
    it("Should return a map of forked tasks and its siblings", () => {
      const taskNames = [
        "shipping_loop_subworkflow_ref_0",
        "shipping_loop_subworkflow_ref_1",
      ];
      const result = relatedNamesToTaskDef(
        taskNames,
        sampleExecutionWithForkJoin.tasks,
      );
      expect(Object.keys(result)).toEqual(taskNames);
    });
  });

  describe("return collapsedTasksStatus array and collapsedTasksStatus", () => {
    it("should return collapsedTasksStatus array", () => {
      const sampleMap = {
        dynamic_ref: {
          taskType: "HTTP",
          status: "COMPLETED",
          loopOver: [],
          inputData: {
            forkedTaskDefs: [
              {
                taskReferenceName: "image_convert_resize_png_300x300_0",
              },
              {
                taskReferenceName: "image_convert_resize_png_200x200_1",
              },
              {
                taskReferenceName: "fallsas",
              },
            ],
          },
        },
        fallsas: {
          taskType: "HTTP",
          status: "COMPLETED",
          loopOver: [],
        },
        image_convert_resize_png_200x200_1: {
          status: "CANCELED",
          loopOver: [],
        },
        image_convert_resize_png_300x300_0: {
          status: "FAILED",
          loopOver: [],
        },
        join_task_ref: {
          status: "CANCELED",
          loopOver: [],
        },
      };
      const sampleExecutionTasks = newDynamicForkSample.tasks;
      const result = taskStatusUpdater(sampleExecutionTasks, sampleMap, []);
      expect(
        result[0].forkTasks[0][0].executionData.collapsedTasksStatus,
      ).toEqual(["FAILED", "CANCELED", "COMPLETED"]);
    });
    it("should return collapsedTasksStatus(card bundle) as FAILED", () => {
      const sampleMap = {
        dynamic_ref: {
          taskType: "HTTP",
          status: "COMPLETED",
          loopOver: [],
          inputData: {
            forkedTaskDefs: [
              {
                taskReferenceName: "image_convert_resize_png_300x300_0",
              },
              {
                taskReferenceName: "image_convert_resize_png_200x200_1",
              },
              {
                taskReferenceName: "fallsas",
              },
            ],
          },
        },
        fallsas: {
          taskType: "HTTP",
          status: "COMPLETED",
          loopOver: [],
        },
        image_convert_resize_png_200x200_1: {
          status: "CANCELED",
          loopOver: [],
        },
        image_convert_resize_png_300x300_0: {
          status: "FAILED",
          loopOver: [],
        },
      };
      const sampleExecutionTasks = newDynamicForkSample.tasks;
      const result = taskStatusUpdater(sampleExecutionTasks, sampleMap, []);
      expect(result[0].forkTasks[0][0].executionData.status).toEqual("FAILED");
    });
    it("should return collapsedTasksStatus(card bundle) as COMPLETED", () => {
      const sampleMap = {
        dynamic_ref: {
          taskType: "HTTP",
          status: "COMPLETED",
          loopOver: [],
          inputData: {
            forkedTaskDefs: [
              {
                taskReferenceName: "image_convert_resize_png_300x300_0",
              },
              {
                taskReferenceName: "image_convert_resize_png_200x200_1",
              },
              {
                taskReferenceName: "fallsas",
              },
            ],
          },
        },
        fallsas: {
          taskType: "HTTP",
          status: "COMPLETED",
          loopOver: [],
        },
        image_convert_resize_png_200x200_1: {
          status: "COMPLETED",
          loopOver: [],
        },
        image_convert_resize_png_300x300_0: {
          status: "COMPLETED",
          loopOver: [],
        },
      };
      const sampleExecutionTasks = newDynamicForkSample.tasks;
      const result = taskStatusUpdater(sampleExecutionTasks, sampleMap, []);
      expect(result[0].forkTasks[0][0].executionData.status).toEqual(
        "COMPLETED",
      );
    });
    it("should return collapsedTasksStatus(card bundle) as TIMED_OUT", () => {
      const sampleMap = {
        dynamic_ref: {
          taskType: "HTTP",
          status: "COMPLETED",
          loopOver: [],
          inputData: {
            forkedTaskDefs: [
              {
                taskReferenceName: "image_convert_resize_png_300x300_0",
              },
              {
                taskReferenceName: "image_convert_resize_png_200x200_1",
              },
              {
                taskReferenceName: "fallsas",
              },
            ],
          },
        },
        fallsas: {
          taskType: "HTTP",
          status: "COMPLETED",
          loopOver: [],
        },
        image_convert_resize_png_200x200_1: {
          status: "COMPLETED",
          loopOver: [],
        },
        image_convert_resize_png_300x300_0: {
          status: "TIMED_OUT",
          loopOver: [],
        },
      };
      const sampleExecutionTasks = newDynamicForkSample.tasks;
      const result = taskStatusUpdater(sampleExecutionTasks, sampleMap, []);
      expect(result[0].forkTasks[0][0].executionData.status).toEqual(
        "TIMED_OUT",
      );
    });
  });

  describe("executionToWorkflowDef with doWhileSelection", () => {
    it("Should build a map with given iteration - 4", () => {
      const selectedIteration = 4;
      const doWhileSelection = [
        {
          doWhileTaskReferenceName: "do_while_ref",
          selectedIteration: selectedIteration,
        },
      ];

      const [_workflowDefinition, executionStatusMap] = executionToWorkflowDef(
        sampleExecutionDoWhile,
        [],
        doWhileSelection,
      );
      expect(Object.keys(executionStatusMap)).toEqual([
        "http_ref_first",
        "do_while_ref",
        "switch_ref",
        "http_second_ref",
        "http_ref_four_ref",
        "http_last_ref",
      ]);
      expect(executionStatusMap["switch_ref"].iteration).toEqual(
        selectedIteration,
      );
      expect(executionStatusMap["http_second_ref"].iteration).toEqual(
        selectedIteration,
      );
      expect(executionStatusMap["http_ref_four_ref"].iteration).toEqual(
        selectedIteration,
      );
    });
    it("Should build a map with given iteration - 1", () => {
      const selectedIteration = 1;
      const doWhileSelection = [
        {
          doWhileTaskReferenceName: "do_while_ref",
          selectedIteration: selectedIteration,
        },
      ];

      const [_workflowDefinition, executionStatusMap] = executionToWorkflowDef(
        sampleExecutionDoWhile,
        [],
        doWhileSelection,
      );
      expect(Object.keys(executionStatusMap)).toEqual([
        "http_ref_first",
        "do_while_ref",
        "switch_ref",
        "http_third_ref",
        "http_ref_cool",
        "http_last_ref",
      ]);
      expect(executionStatusMap["switch_ref"].iteration).toEqual(
        selectedIteration,
      );
      expect(executionStatusMap["http_third_ref"].iteration).toEqual(
        selectedIteration,
      );
      expect(executionStatusMap["http_ref_cool"].iteration).toEqual(
        selectedIteration,
      );
    });
    it("Should build a map with muliti-dowhile given iteration 1,4", () => {
      const selectedIterationForFirstDoWhile = 1;
      const selectedIterationForSecondDoWhile = 4;
      const doWhileSelection = [
        {
          doWhileTaskReferenceName: "do_while_ref",
          selectedIteration: selectedIterationForFirstDoWhile,
        },
        {
          doWhileTaskReferenceName: "do_while_ref_1",
          selectedIteration: selectedIterationForSecondDoWhile,
        },
      ];

      const [_workflowDefinition, executionStatusMap] = executionToWorkflowDef(
        sampleExecutionMultiDoWhile,
        [],
        doWhileSelection,
      );
      expect(Object.keys(executionStatusMap)).toEqual(
        expect.arrayContaining([
          "do_while_ref",
          "http_ref_five",
          "http_last_ref",
          "do_while_ref_1",
          "http_new_dowhile_one_ref",
          "http_ref_first",
          "switch_ref",
          "switch_ref_1",
        ]),
      );
      // for the first doWhile
      expect(executionStatusMap["switch_ref"].iteration).toEqual(
        selectedIterationForFirstDoWhile,
      );
      expect(executionStatusMap["http_ref_five"].iteration).toEqual(
        selectedIterationForFirstDoWhile,
      );

      // for the second doWhile
      expect(executionStatusMap["switch_ref_1"].iteration).toEqual(
        selectedIterationForSecondDoWhile,
      );
      expect(executionStatusMap["http_new_dowhile_one_ref"].iteration).toEqual(
        selectedIterationForSecondDoWhile,
      );
    });
  });

  describe("executionToWorkflowDef without doWhileSelection", () => {
    it("Should build expected map", () => {
      const [_workflowDefinition, executionStatusMap] = executionToWorkflowDef(
        sampleExecutionDoWhile,
        [],
      );
      expect(Object.keys(executionStatusMap)).toEqual([
        "http_ref_first",
        "do_while_ref",
        "switch_ref",
        "http_third_ref",
        "http_ref_cool",
        "http_second_ref",
        "http_ref_four_ref",
        "http_ref_five",
        "http_last_ref",
      ]);
    });
  });
});

describe("doWhileSelectionForStatusMap", () => {
  it("function works normally, returning expected result - single DoWhile selected", () => {
    const doWhileSelection = [
      {
        doWhileTaskReferenceName: "do_while_ref",
        selectedIteration: 1,
      },
    ];
    const result = doWhileSelectionForStatusMap(
      doWhileSelection,
      sampleStatusMap,
    );
    expect(result).toEqual(doWhileSelectionForStatusMapResultSingleDoWhile);
  });
  it("function works normally, returning expected result - multiple Dowhile selected", () => {
    const doWhileSelection = [
      {
        doWhileTaskReferenceName: "do_while_ref",
        selectedIteration: 1,
      },
      {
        doWhileTaskReferenceName: "do_while_ref_1",
        selectedIteration: 6,
      },
    ];
    const result = doWhileSelectionForStatusMap(
      doWhileSelection,
      sampleStatusMap,
    );
    expect(result).toEqual(doWhileSelectionForStatusMapResultMultiDowhile);
  });
  it("should handle missing referenced task gracefully (currentAssociatedTask is undefined)", () => {
    const doWhileSelection = [
      {
        doWhileTaskReferenceName: "do_while_ref",
        selectedIteration: 1,
      },
    ];
    // statusMap is missing a referenced task for this iteration
    const minimalStatusMap = {
      do_while_ref: {
        taskType: "DO_WHILE",
        status: "COMPLETED",
        inputData: { number: 10 },
        referenceTaskName: "do_while_ref",
        retryCount: 0,
        seq: 2,
        pollCount: 0,
        taskDefName: "do_while",
        scheduledTime: 1,
        startTime: 1,
        endTime: 2,
        updateTime: 2,
        startDelayInSeconds: 0,
        retried: false,
        executed: true,
        callbackFromWorker: true,
        responseTimeoutSeconds: 0,
        workflowInstanceId: "wf1",
        workflowType: "test",
        taskId: "tid1",
        callbackAfterSeconds: 0,
        outputData: {
          1: {
            missing_ref: { some: "data" },
          },
        },
        loopOver: [],
      },
      // Note: 'missing_ref' is not present in the statusMap
    };
    const result = doWhileSelectionForStatusMap(
      doWhileSelection,
      minimalStatusMap,
    );
    // Should include 'missing_ref' with undefined or empty values, but not throw
    expect(result).toHaveProperty("missing_ref");
    expect(result.missing_ref).toBeDefined();
    // Should be an object, but all values undefined except loopOver/parentLoop
    expect(result.missing_ref.loopOver).toBeUndefined();
  });
});
