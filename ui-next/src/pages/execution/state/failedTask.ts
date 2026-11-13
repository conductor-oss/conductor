import { ExecutionTask } from "types/Execution";
import { TaskStatus } from "types/TaskStatus";

/**
 * The task a failed execution failed at, for "Jump to task": the one the server names in
 * failedTaskId, else (an older server) the first FAILED task with a reason. Tasks that failed
 * and were retried, or whose failure the workflow handled, also have status FAILED, so the
 * fallback can name one of them instead.
 */
export const failedTaskOf = (
  tasks: ExecutionTask[] | undefined,
  failedTaskId?: string,
): ExecutionTask | undefined => {
  if (failedTaskId) {
    const named = tasks?.find((task) => task?.taskId === failedTaskId);
    if (named) {
      return named;
    }
  }
  return tasks?.find(
    (task) => task?.status === TaskStatus.FAILED && task?.reasonForIncompletion,
  );
};
