import { DoWhileTaskDef } from "types/TaskType";
import { TaskDef } from "types/common";

export const genSampleScripts = (task: DoWhileTaskDef | undefined) => ({
  fixed_number: {
    loopCondition: `(function () {\n  if (${
      task?.taskReferenceName
        ? `$.${task?.taskReferenceName}`
        : `$.do_while_ref`
    }['iteration'] < $.number) {\n    return true;\n  }\n  return false;\n})();`,
    inputParameters: { number: 5 },
    loopOver: [],
  },
  iterate_over_array: {
    loopCondition: `(function () {\n  if (${
      task?.taskReferenceName
        ? `$.${task?.taskReferenceName}`
        : `$.do_while_ref`
    }['iteration'] < $.myArray.length) {\n    return true;\n  }\n  return false;\n})();`,
    inputParameters: { myArray: [{ name: "Orkes" }, { year: 2024 }] },
    loopOver: [
      {
        name: "inline_sample",
        taskReferenceName: "inline_sample_ref",
        type: "INLINE",
        inputParameters: {
          expression:
            "(function () { \n  const current = $.iteration;\n  return current;\n})();",
          evaluatorType: "graaljs",
          iteration:
            "${" +
            (task?.taskReferenceName
              ? task?.taskReferenceName
              : "do_while_ref") +
            ".output}",
        },
      },
    ] as unknown as TaskDef[],
  },
});
