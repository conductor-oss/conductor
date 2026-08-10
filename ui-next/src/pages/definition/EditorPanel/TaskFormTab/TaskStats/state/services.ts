import { fetchContextNonHook, fetchWithContext } from "plugins/fetch";
import { getCurrentUnixTimestamp, getUnixTimestampHoursAgo } from "utils/date";
import { logger } from "utils/logger";
import { queryClient } from "../../../../../../queryClient";
import { TaskStatsMachineContext } from "./types";

const fetchContext = fetchContextNonHook();

const TASK_METRICS_PATH = `/metrics/task`;

const RESOLUTION_FOR_24 = 0.05;

export const fetchForTaskMetrics = async ({
  authHeaders: headers,
  startHoursBack,
  taskName,
}: TaskStatsMachineContext) => {
  const timestampNow = getCurrentUnixTimestamp();
  const endTimeStamp = getUnixTimestampHoursAgo(startHoursBack, timestampNow);

  const step = (startHoursBack * RESOLUTION_FOR_24) / 24;
  /* logger.info( */
  /*   "Hittin prometheus proxy using statTimeStamp as ", */
  /*   startTimestamp, */
  /*   timestampNow */
  /* ); */
  const url = `${TASK_METRICS_PATH}/${taskName}?start=${endTimeStamp}&end=${timestampNow}&step=${Math.floor(
    step * 60 * 60,
  )}`;
  try {
    const response = await queryClient.fetchQuery(
      [fetchContext.stack, url],
      () => fetchWithContext(url, fetchContext, { headers }),
    );
    return response;
  } catch (error) {
    logger.error("Fetching task list page", error);
    return Promise.reject({ message: "Error fetching task list page" });
  }
};
