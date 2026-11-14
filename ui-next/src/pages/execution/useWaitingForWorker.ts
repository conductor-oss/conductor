import { WorkflowExecution } from "types/Execution";
import { useTaskQueueInfo } from "utils/query";
import { lastPoll, waitingCode, waitingForWorker } from "./waiting";

/** Why a native execution waits for a worker of its code, or undefined when it does not. */
export const useWaitingForWorker = (
  execution?: WorkflowExecution,
): string | undefined => {
  const waiting = waitingCode(execution);
  const { data } = useTaskQueueInfo(waiting?.codeType ?? "");
  return waiting
    ? waitingForWorker(waiting, lastPoll(waiting, data?.pollData), Date.now())
    : undefined;
};
