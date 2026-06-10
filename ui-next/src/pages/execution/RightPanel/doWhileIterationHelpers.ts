import { TaskStatus } from "types/TaskStatus";

/**
 * Returns the display status for a single DO_WHILE iteration in the fallback
 * rendering path (where the server has not returned per-iteration status data).
 *
 * - If the iteration's numeric key exists in the parent task's outputData, the
 *   iteration completed → COMPLETED.
 * - Otherwise the loop is still active on this iteration or it
 *   failed/timed-out here → inherit the parent task's own status.
 */
export function deriveFallbackIterationStatus(
  iteration: number,
  outputData: Record<string, unknown>,
  taskStatus: TaskStatus,
): TaskStatus {
  return Object.prototype.hasOwnProperty.call(outputData, String(iteration))
    ? TaskStatus.COMPLETED
    : taskStatus;
}

export function getOrderedIterationKeys(
  outputData: Record<string, any>,
  selectedTask: { iteration?: number },
): number[] {
  const numericOutputKeys = Object.keys(outputData)
    .map(Number)
    .filter((k) => !isNaN(k));

  const maxFromOutputData =
    numericOutputKeys.length > 0 ? Math.max(...numericOutputKeys) : 0;
  const maxFromTask =
    typeof selectedTask.iteration === "number" ? selectedTask.iteration : 0;
  const totalIterations = Math.max(maxFromOutputData, maxFromTask);

  if (totalIterations > 0) {
    return Array.from(
      { length: totalIterations },
      (_, i) => totalIterations - i,
    );
  }

  numericOutputKeys.sort((a, b) => b - a);
  return numericOutputKeys;
}
