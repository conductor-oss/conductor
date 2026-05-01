import { useFetch } from "./common";
import { useMemo } from "react";
import { fetchWithContext, useFetchContext } from "../plugins/fetch";
import { useMutation, useQuery } from "react-query";
import qs from "qs";

export function useSchedulerEnabled() {
  const fetchContext = useFetchContext();
  const result = useQuery(
    [fetchContext.stack, "schedulerEnabled"],
    () =>
      fetchWithContext("/scheduler/schedules", fetchContext)
        .then(() => true)
        .catch((err) => {
          const errStr = String(err);
          if (errStr.includes("404") || errStr.includes("Not Found")) {
            return false;
          }
          // Non-404 errors (e.g. 500) — scheduler is enabled but broken
          return true;
        }),
    {
      enabled: fetchContext.ready,
      staleTime: 5 * 60 * 1000,
      retry: false,
    }
  );
  return {
    enabled: result.data !== false,
    isLoading: result.isLoading,
  };
}

export function useSchedulers() {
  return useFetch(["schedulers"], "/scheduler/schedules");
}

export function useScheduler(schedulerName, defaultScheduler) {
  const path = schedulerName
    ? `/scheduler/schedules/${encodeURIComponent(schedulerName)}`
    : null;

  const query = useFetch(["scheduler", schedulerName], path);

  const scheduler = useMemo(() => {
    return query.data || defaultScheduler;
  }, [query.data, defaultScheduler]);

  return {
    ...query,
    scheduler,
  };
}

export function useSchedulerNames() {
  const { data } = useSchedulers();
  return useMemo(
    () => (data ? Array.from(new Set(data.map((def) => def.name))).sort() : []),
    [data]
  );
}

export function useSaveScheduler(callbacks) {
  const path = "/scheduler/schedules";
  const fetchContext = useFetchContext();

  return useMutation(
    ({ body }) => {
      return fetchWithContext(path, fetchContext, {
        method: "post",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      });
    },
    callbacks
  );
}

const STALE_TIME_SEARCH = 60000; // 1 min

export function useSchedulerExecutionSearch(searchObj) {
  const fetchContext = useFetchContext();
  const pathRoot = "/scheduler/search/executions?";

  return useQuery(
    [fetchContext.stack, pathRoot, searchObj],
    () => {
      const { rowsPerPage, page, sort, freeText, query } = searchObj;
      const path =
        pathRoot +
        qs.stringify({
          start: (page - 1) * rowsPerPage,
          size: rowsPerPage,
          sort: sort,
          freeText: freeText,
          query: query,
        });
      return fetchWithContext(path, fetchContext);
    },
    {
      enabled: fetchContext.ready,
      keepPreviousData: true,
      staleTime: STALE_TIME_SEARCH,
    }
  );
}

export function useDeleteScheduler(callbacks) {
  const fetchContext = useFetchContext();

  return useMutation(
    ({ name }) => {
      return fetchWithContext(
        `/scheduler/schedules/${encodeURIComponent(name)}`,
        fetchContext,
        { method: "delete" }
      );
    },
    callbacks
  );
}
