import { fetchWithContext, useFetchContext } from "plugins/fetch";
import qs from "qs";
import { useMemo } from "react";
import { useQuery } from "react-query";
import { IScheduleDto, SchedulerSearchResult } from "types/Schedulers";
import { STALE_TIME_SEARCH, useAuthHeaders } from "utils/query";

const SCHEDULER_SEARCH_PATH = "/scheduler/schedules/search?";
/** Server-side cap for scheduler search page size. */
const SCHEDULER_SEARCH_MAX_SIZE = 1000;

export const useGetSchedulerDefinitions = () => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };

  return useQuery<IScheduleDto[]>(
    [fetchContext.stack, SCHEDULER_SEARCH_PATH, "all-names"],
    async () => {
      const path =
        SCHEDULER_SEARCH_PATH +
        qs.stringify({ start: 0, size: SCHEDULER_SEARCH_MAX_SIZE });
      const result = (await fetchWithContext(
        path,
        fetchContext,
        fetchParams,
      )) as SchedulerSearchResult | null;
      return result?.results ?? [];
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
        ...(searchParams.name && { freeText: searchParams.name }),
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
