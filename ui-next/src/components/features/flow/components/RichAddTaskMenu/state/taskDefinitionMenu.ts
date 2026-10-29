import { TaskType } from "types/common";
import { BaseTaskMenuItem, RichAddMenuTabs, TaskDefinition } from "./types";

/** Hide only the retired built-in worker, not customer tasks sharing its name. */
export const createWorkerTaskMenuItems = (
  taskDefinitions: TaskDefinition[],
): BaseTaskMenuItem[] =>
  taskDefinitions
    .filter(
      (task) =>
        task.name !== "AWS_LAMBDA" ||
        task.description !== "Remote AWS_LAMBDA worker" ||
        task.ownerEmail !== "orkes-workers@apps.orkes.io",
    )
    .map((task) => ({
      category: RichAddMenuTabs.WORKERS_TAB,
      name: task.name,
      description: task.description,
      type: TaskType.SIMPLE,
    }));
