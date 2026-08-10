import { ExecutionTask } from "types/Execution";

/**
 * Task output is useful while a worker is still running, particularly for
 * streaming/progress-oriented workers. Retry state must not hide data that the
 * server actually returned.
 */
export const getTaskOutputForDisplay = (
  task: Pick<ExecutionTask, "outputData">,
) => task.outputData;
