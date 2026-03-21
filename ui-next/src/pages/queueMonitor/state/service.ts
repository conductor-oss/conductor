import { queryClient } from "queryClient";
import { fetchWithContext, fetchContextNonHook } from "plugins/fetch";
import { QueueMonitorMachineContext } from "./types";
import { logger } from "utils/logger";
import { hasNoQueryParams, filterOptionToQueryParams } from "../helpers";

const fetchContext = fetchContextNonHook();

const queuePollDataPath = `/tasks/queue/polldata/all`;

const LOCAL_STORAGE_KEY = "queueMonitorRefreshSeconds";

export const fetchForPollData = async ({
  authHeaders: headers,
  filterOptions,
}: QueueMonitorMachineContext) => {
  const url = hasNoQueryParams(filterOptions)
    ? queuePollDataPath
    : `${queuePollDataPath}?${filterOptionToQueryParams(filterOptions)}`;

  logger.info("Will hit path to fetch for tasks ", url, filterOptions);
  try {
    const response = await queryClient.fetchQuery(
      [fetchContext.stack, url],
      () => fetchWithContext(url, fetchContext, { headers }),
    );
    return response;
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
