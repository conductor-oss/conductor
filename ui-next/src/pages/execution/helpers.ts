import { ExecutionTask } from "types/Execution";
import _nth from "lodash/nth";
import { StatusMap } from "./state/StatusMapTypes";
type SeqResult = {
  seqNumber: number;
  idx: number;
};

export const taskWithLatestIteration = (
  tasksList: ExecutionTask[] = [],
  taskReferenceName = "",
  taskId?: string,
) => {
  const filteredTasks = tasksList.filter(
    (task) =>
      task.workflowTask.taskReferenceName === taskReferenceName ||
      task.taskId === taskId ||
      task.referenceTaskName === taskReferenceName,
  );

  if (filteredTasks && filteredTasks.length === 1) {
    // task without any retry/iteration
    return _nth(filteredTasks, 0);
  } else if (filteredTasks && filteredTasks.length > 1) {
    const result = filteredTasks.reduce(
      (acc: SeqResult, task, idx) => {
        if (task.seq && acc.seqNumber < Number(task.seq)) {
          return { seqNumber: Number(task.seq), idx };
        }
        return acc;
      },
      { seqNumber: 0, idx: -1 },
    );

    if (result.idx > -1) {
      return _nth(filteredTasks, result.idx);
    }
  }
  return undefined;
};

export function findTaskFromExecutionStatusMapById(
  mapObject: StatusMap,
  id: string | null,
) {
  const keys = Object.keys(mapObject);

  for (const key of keys) {
    const item = mapObject[key];
    const found = item?.loopOver?.find((loopItem) => loopItem?.taskId === id);
    if (found) {
      return found;
    }
  }

  return null; // return null if not found
}
