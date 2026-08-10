import { SimpleTaskDef, TaskStatus, TaskType } from "types";
import { maybeEdgeData } from "./common";

describe("maybeEdgeData", () => {
  const imageResizeTask: SimpleTaskDef = {
    name: "image_convert_resize_jim",
    taskReferenceName: "image_convert_resize_ref",
    inputParameters: {
      fileLocation: "${workflow.input.fileLocation}",
      outputFormat: "${workflow.input.recipeParameters.outputFormat}",
      outputWidth: "${workflow.input.recipeParameters.outputSize.width}",
      outputHeight: "${workflow.input.recipeParameters.outputSize.height}",
    },
    type: TaskType.SIMPLE,
    optional: false,
  };
  const uploadImageTask: SimpleTaskDef = {
    name: "upload_toS3_jim",
    taskReferenceName: "upload_toS3_ref",
    inputParameters: {
      fileLocation: "${image_convert_resize_ref.output.fileLocation}",
    },
    type: TaskType.SIMPLE,
    optional: false,
  };

  it("Should return status completed if both previous task and current task is complete", () => {
    const edges = maybeEdgeData(
      {
        ...imageResizeTask,
        executionData: {
          status: TaskStatus.COMPLETED,
          executed: true,
          attempts: 0,
        },
      },
      {
        ...uploadImageTask,
        executionData: {
          status: TaskStatus.COMPLETED,
          executed: true,
          attempts: 0,
        },
      },
    );
    expect(edges).toEqual({
      data: {
        status: "COMPLETED",
        unreachableEdge: false,
      },
    });
  });
  it("Should return empty if the next task is PENDING", () => {
    const edges = maybeEdgeData(
      {
        ...imageResizeTask,
        executionData: {
          status: TaskStatus.PENDING,
          executed: true,
          attempts: 0,
        },
      },
      {
        ...uploadImageTask,
        executionData: {
          status: TaskStatus.COMPLETED,
          executed: true,
          attempts: 0,
        },
      },
    );
    expect(edges).toEqual({
      data: {
        unreachableEdge: false,
      },
    });
  });

  it("Should return completed. if the first task is completed and the next task is FAILED", () => {
    const edges = maybeEdgeData(
      {
        ...imageResizeTask,
        executionData: {
          status: TaskStatus.FAILED,
          executed: true,
          attempts: 0,
        },
      },
      {
        ...uploadImageTask,
        executionData: {
          status: TaskStatus.COMPLETED,
          executed: true,
          attempts: 0,
        },
      },
    );
    expect(edges).toEqual({
      data: {
        status: "COMPLETED",
        unreachableEdge: false,
      },
    });
  });
});
