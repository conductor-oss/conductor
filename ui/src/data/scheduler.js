import { useMemo } from "react";
import { useQuery, useMutation } from "react-query";
import { useFetchContext, fetchWithContext } from "../plugins/fetch";
import { useFetch } from "./common";
import qs from "qs";

export function useSchedulerDefs() {
  return useFetch(["schedulerDefs"], "/scheduler/schedules");
}

export function useScheduleNames() {
  const { data } = useSchedulerDefs();
  return useMemo(
    () => (data ? data.map((s) => s.name).sort() : []),
    [data]
  );
}

export function useSchedulerDef(name, defaultSchedule) {
  return useFetch(
    ["schedulerDef", name],
    name ? `/scheduler/schedules/${name}` : null,
    { enabled: !!name },
    defaultSchedule
  );
}

export function useSaveScheduler(callbacks) {
  const fetchContext = useFetchContext();
  return useMutation(
    ({ body }) =>
      fetchWithContext("/scheduler/schedules", fetchContext, {
        method: "post",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
    callbacks
  );
}

export function useDeleteScheduler(callbacks) {
  const fetchContext = useFetchContext();
  return useMutation(
    ({ name }) =>
      fetchWithContext(`/scheduler/schedules/${name}`, fetchContext, {
        method: "delete",
      }),
    callbacks
  );
}

export function usePauseScheduler(callbacks) {
  const fetchContext = useFetchContext();
  return useMutation(
    ({ name }) =>
      fetchWithContext(`/scheduler/schedules/${name}/pause`, fetchContext, {
        method: "put",
      }),
    callbacks
  );
}

export function useResumeScheduler(callbacks) {
  const fetchContext = useFetchContext();
  return useMutation(
    ({ name }) =>
      fetchWithContext(`/scheduler/schedules/${name}/resume`, fetchContext, {
        method: "put",
      }),
    callbacks
  );
}

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
    }
  );
}
