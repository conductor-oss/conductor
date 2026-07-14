import { fetchWithContext, useFetchContext } from "plugins/fetch";
import qs from "qs";
import { useMemo } from "react";
import { useQuery } from "react-query";
import { IScheduleDto, SchedulerSearchResult } from "types/Schedulers";
import { STALE_TIME_SEARCH, useAuthHeaders } from "utils/query";

const SCHEDULER_PATH = "/scheduler/schedules";
const SCHEDULER_SEARCH_PATH = "/scheduler/schedules/search?";

/** Full schedule list (used by Scheduler Executions name dropdown / similar lookups). */
export const useGetSchedulerDefinitions = () => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };

  return useQuery<IScheduleDto[]>(
    [fetchContext.stack, SCHEDULER_PATH],
    () => {
      const path = SCHEDULER_PATH;
      return fetchWithContext(path, fetchContext, fetchParams);
    },
    {
      enabled: fetchContext.ready,
      keepPreviousData: true,
      staleTime: STALE_TIME_SEARCH,
      retry: (failureCount: number, error: any) => {
        if (error?.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount > 3;
      },
    },
  );
};

export interface SchedulerSearchParams {
  start?: number;
  size?: number;
  sort?: string;
  workflowName?: string;
  name?: string;
  paused?: boolean;
}

export const useGetSchedulerDefinitionsWithPagination = (
  searchParams: SchedulerSearchParams,
) => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };

  return useQuery<SchedulerSearchResult>(
    [fetchContext.stack, SCHEDULER_SEARCH_PATH, searchParams],
    () => {
      const params = {
        start: searchParams.start ?? 0,
        size: searchParams.size ?? 100,
        ...(searchParams.sort && { sort: searchParams.sort }),
        ...(searchParams.workflowName && {
          workflowName: searchParams.workflowName,
        }),
        // Send both param names: OSS backend uses "scheduleName", enterprise uses "name".
        // Each backend picks the one it understands and ignores the other.
        ...(searchParams.name && {
          scheduleName: searchParams.name,
          name: searchParams.name,
        }),
        ...(searchParams.paused !== undefined && {
          paused: searchParams.paused,
        }),
      };
      const path = SCHEDULER_SEARCH_PATH + qs.stringify(params);
      return fetchWithContext(path, fetchContext, fetchParams);
    },
    {
      enabled: fetchContext.ready,
      keepPreviousData: true,
      staleTime: STALE_TIME_SEARCH,
      retry: (failureCount: number, error: any) => {
        if (error?.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount > 3;
      },
    },
  );
};

export function useScheduleNames() {
  const { data } = useGetSchedulerDefinitions();
  return useMemo(() => (data ? data.map((def) => def.name) : []), [data]);
}
