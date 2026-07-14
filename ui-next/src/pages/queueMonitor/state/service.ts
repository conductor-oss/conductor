import { queryClient } from "queryClient";
import { fetchWithContext, fetchContextNonHook } from "plugins/fetch";
import { PollData, QueueMonitorMachineContext } from "./types";
import { logger } from "utils/logger";
import { createQueueMonitorResponse } from "../helpers";

const fetchContext = fetchContextNonHook();

const queuePollDataPath = `/tasks/queue/polldata/all`;
const queueSizesPath = `/tasks/queue/all`;

const LOCAL_STORAGE_KEY = "queueMonitorRefreshSeconds";

export const fetchForPollData = async ({
  authHeaders: headers,
  filterOptions,
}: QueueMonitorMachineContext) => {
  logger.info("Fetching task queue sizes and poll data", filterOptions);
  try {
    const [pollData, queueSizes] = await Promise.all([
      queryClient.fetchQuery<PollData[]>(
        [fetchContext.stack, queuePollDataPath],
        () =>
          fetchWithContext(queuePollDataPath, fetchContext, {
            headers,
          }),
      ),
      queryClient.fetchQuery<Record<string, number>>(
        [fetchContext.stack, queueSizesPath],
        () =>
          fetchWithContext(queueSizesPath, fetchContext, {
            headers,
          }),
      ),
    ]);

    return createQueueMonitorResponse(queueSizes, pollData, filterOptions);
  } catch (error: any) {
    logger.error("Fetching task list page", error);
    return Promise.reject({
      message:
        error.status === 403
          ? "It seems like you do not have permissions to view this page. Please check with your cluster administrator."
          : "Error fetching task list page",
    });
  }
};

export const saveOrderAndVisibility = async (
  context: QueueMonitorMachineContext,
) => {
  const { refetchDuration } = context;
  window.localStorage.setItem(LOCAL_STORAGE_KEY, `${refetchDuration}`);

  return true;
};

export const maybePullOrderAndVisibility = async (
  context: QueueMonitorMachineContext,
) => {
  const { refetchDuration } = context;
  const savedOrder = window.localStorage.getItem(LOCAL_STORAGE_KEY);
  if (savedOrder) {
    return parseInt(savedOrder, 10);
  }
  return refetchDuration;
};
