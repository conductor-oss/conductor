import { queryClient } from "queryClient";
import { PageType, RefreshMachineContext } from "./types";
import { fetchContextNonHook, fetchWithContext } from "plugins/fetch";
import { groupDataByMessageId } from "pages/eventMonitor/utils";
import { AuthHeaders } from "types/common";

const fetchContext = fetchContextNonHook();
const EVENT_MONITOR_URL = "event/execution";

const fetchEventMonitorData = async (
  headers: AuthHeaders,
  eventName?: string,
  timeRange?: number,
) => {
  try {
    if (eventName) {
      let url = `event/execution/${eventName}`;
      if (timeRange && timeRange > 0) {
        url += `?from=${timeRange}`;
      }

      const data = await queryClient.fetchQuery([fetchContext.stack, url], () =>
        fetchWithContext(url, fetchContext, { headers }),
      );

      return groupDataByMessageId(data);
    }
  } catch (error) {
    console.error("Error fetching event monitor data:", error);
    throw new Error("Failed to fetch event monitor data");
  }
};

const fetchEventListingData = async (headers: AuthHeaders) => {
  try {
    if (headers) {
      const data = await queryClient.fetchQuery(
        [fetchContext.stack, EVENT_MONITOR_URL],
        () => fetchWithContext(EVENT_MONITOR_URL, fetchContext, { headers }),
      );
      return data;
    }
  } catch (error) {
    console.error("Error fetching event list data:", error);
    throw new Error("Failed to fetch event list data");
  }
};
export const fetchEventData = async ({
  pageType,
  authHeaders,
  eventName,
  timeRange,
}: RefreshMachineContext) => {
  if (!authHeaders) {
    throw new Error("authHeaders is not defined");
  }

  try {
    if (pageType === PageType.EVENT_LISTING) {
      return await fetchEventListingData(authHeaders);
    }
    return await fetchEventMonitorData(authHeaders, eventName, timeRange);
  } catch (error) {
    console.error("Error fetching event data:", error);
    throw new Error("Failed to fetch event data");
  }
};
