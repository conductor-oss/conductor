import _ from "lodash";
import { useMemo } from "react";
import { useQuery, useQueries, useMutation } from "react-query";
import qs from "qs";
import { useFetchContext, fetchWithContext } from "../plugins/fetch";
import { useFetch } from "./common";
import Path from "../utils/path";

const STALE_TIME_SEARCH = 60000; // 1 min

export function useTask(taskName, defaultTask) {
  let path;
  if (taskName) {
    path = `/metadata/taskdefs/${taskName}`;
  }
  return useFetch(["taskDef", taskName], path, {}, defaultTask);
}

export function useTaskSearch({ searchReady, ...searchObj }) {
  const fetchContext = useFetchContext();
  const pathRoot = "/tasks/search?";
  const { rowsPerPage, page, sort, freeText, query } = searchObj;

  const isEmptySearch = _.isEmpty(query) && freeText === "*";

  return useQuery(
    [fetchContext.stack, pathRoot, searchObj],
    () => {
      if (isEmptySearch) {
        return {
          results: [],
          totalHits: 0,
        };
      } else {
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
      }
      // staletime to ensure stable view when paginating back and forth (even if underlying results change)
    },
    {
      enabled: fetchContext.ready,
      keepPreviousData: true,
      staleTime: STALE_TIME_SEARCH,
    }
  );
}

export function usePollData(taskName) {
  const fetchContext = useFetchContext();
  const pollDataPath = `/tasks/queue/polldata?taskType=${taskName}`;

  return useQuery(
    [fetchContext.stack, pollDataPath],
    () => fetchWithContext(pollDataPath, fetchContext),
    {
      enabled: fetchContext.ready && !_.isEmpty(taskName),
    }
  );
}

export function useQueueSize(taskName, domain) {
  const fetchContext = useFetchContext();
  const path = new Path("/tasks/queue/size");
  path.search.append("taskType", taskName);

  if (!_.isUndefined(domain)) {
    path.search.append("domain", domain);
  }

  return useQuery([fetchContext.stack, "queueSize", taskName, domain], () =>
    fetchWithContext(path.toString(), fetchContext, {
      enabled: fetchContext.ready,
    })
  );
}

export function useQueueSizes(taskName, domains) {
  const fetchContext = useFetchContext();

  return useQueries(
    domains
      ? domains.map((domain) => {
          const path = new Path("/tasks/queue/size");
          path.search.append("taskType", taskName);

          if (!_.isUndefined(domain)) {
            path.search.append("domain", domain);
          }

          return {
            queryKey: [fetchContext.stack, "queueSize", taskName, domain],
            queryFn: async () => {
              const result = await fetchWithContext(
                path.toString(),
                fetchContext
              );
              return {
                domain: domain,
                size: result,
              };
            },
            enabled: fetchContext.ready && !!domains,
          };
        })
      : []
  );
}

export function useTaskNames() {
  const { data } = useTaskDefs();
  return useMemo(
    () => (data ? Array.from(new Set(data.map((def) => def.name))).sort() : []),
    [data]
  );
}

export function useTaskDefs() {
  return useFetch(["taskDefs"], "/metadata/taskdefs");
}

export function useSaveTask(callbacks) {
  const path = "/metadata/taskdefs";
  const fetchContext = useFetchContext();

  return useMutation(({ body, isNew }) => {
    return fetchWithContext(path, fetchContext, {
      method: isNew ? "post" : "put",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(isNew ? [body] : body), // Note: application of [] is opposite of workflow
    });
  }, callbacks);
}
