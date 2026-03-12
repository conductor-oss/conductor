import { extractExecutionDataOrEmpty } from "./common";
import { BOTTOM_PORT_MARGIN, taskToSize } from "./layout";
import { TerminateTaskDef, Crumb } from "types";
import { NodeData } from "reaflow";
import { NodeTaskData } from "./types";

export const taskToTerminateNode = (
  task: TerminateTaskDef,
  crumbs: Crumb[] = [],
): NodeData<NodeTaskData<TerminateTaskDef>> => {
  const { taskReferenceName, name } = task;
  const { width, height } = taskToSize(task);
  return {
    id: taskReferenceName,
    text: name,
    data: {
      task,
      crumbs,
      ...extractExecutionDataOrEmpty(task),
    },
    width,
    // Add a bit of margin to the bottom
    // to avoid overlapping arrow edges and ports
    height: height + BOTTOM_PORT_MARGIN,
  };
};
