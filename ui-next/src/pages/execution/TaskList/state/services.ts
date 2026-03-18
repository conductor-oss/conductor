import { queryClient } from "../../../../queryClient";
import { fetchWithContext, fetchContextNonHook } from "plugins/fetch";
import { TaskListMachineContext } from "./types";
import { logger } from "utils/logger";
import { UrlOptions } from "utils/toMaybeQueryString";
import qs from "qs";
import _isEmpty from "lodash/isEmpty";

const fetchContext = fetchContextNonHook();
const getQueryString = (content: any) => {
  return _isEmpty(content)
    ? ""
    : `?${qs.stringify(content, { indices: false })}`;
};

export const fetchForTasksService = async ({
  authHeaders: headers,
  executionId,
  startIndex = 0,
  rowsPerPage = 15,
  filterStatus,
}: TaskListMachineContext) => {
  const executionTasksPath = `/workflow/${executionId}/tasks${getQueryString({
    status: filterStatus,
    count: rowsPerPage,
    start: startIndex,
  } as unknown as UrlOptions)}`;
  logger.info("Will hit path to fetch for tasks ", executionTasksPath);
  try {
    const response = await queryClient.fetchQuery(
      [fetchContext.stack, executionTasksPath],
      () => fetchWithContext(executionTasksPath, fetchContext, { headers }),
    );
    return response;
  } catch (error) {
    logger.error("Fetching task list page", error);
    return Promise.reject({ message: "Error fetching task list page" });
  }
};
