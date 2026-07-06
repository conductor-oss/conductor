import { fetchWithContext, useFetchContext } from "plugins/fetch";
import qs from "qs";
import { useMemo } from "react";
import { useQuery } from "react-query";
import { IScheduleDto, SchedulerSearchResult } from "types/Schedulers";
import { STALE_TIME_SEARCH, useAuthHeaders } from "utils/query";

const SCHEDULER_PATH = "/scheduler/schedules";
const SCHEDULER_SEARCH_PATH = "/scheduler/schedules/search?";

export const useGetSchedulerDefinitions = (options?: { enabled?: boolean }) => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };
  const enabled = options?.enabled ?? true;

  return useQuery<IScheduleDto[]>(
    [fetchContext.stack, SCHEDULER_PATH],
    () => {
      const path = SCHEDULER_PATH;
      return fetchWithContext(path, fetchContext, fetchParams);
      // staletime to ensure stable view when paginating back and forth (even if underlying results change)
    },
    {
      enabled: fetchContext.ready && enabled,
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
  /** Maps to search `freeText` (table search box). */
  name?: string;
  /** Maps to search `name` (schedule name filter). */
  scheduleName?: string;
  paused?: boolean;
}

export const useGetSchedulerDefinitionsWithPagination = (
  searchParams: SchedulerSearchParams,
  options?: { enabled?: boolean },
) => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };
  const enabled = options?.enabled ?? true;

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
        ...(searchParams.name && { freeText: searchParams.name }),
        ...(searchParams.scheduleName && { name: searchParams.scheduleName }),
        ...(searchParams.paused !== undefined && {
          paused: searchParams.paused,
        }),
      };
      const path = SCHEDULER_SEARCH_PATH + qs.stringify(params);
      return fetchWithContext(path, fetchContext, fetchParams);
    },
    {
      enabled: fetchContext.ready && enabled,
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
