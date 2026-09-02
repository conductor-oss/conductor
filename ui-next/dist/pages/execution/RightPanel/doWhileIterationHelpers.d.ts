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
export declare function deriveFallbackIterationStatus(iteration: number, outputData: Record<string, unknown>, taskStatus: TaskStatus): TaskStatus;
/**
 * Returns true when the iteration's output data entry carries the _summarized
 * sentinel, or when the key is absent and the task is no longer processing
 * (implying older records were pruned by keepLastN).
 */
export declare function isIterationSummarized(option: number, outputData: Record<string, unknown>, isTaskProcessing: boolean): boolean;
export declare function getOrderedIterationKeys(outputData: Record<string, any>, selectedTask: {
    iteration?: number;
}): number[];
