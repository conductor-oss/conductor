import _ from "lodash";
import { useMemo } from "react";
import {
  useQuery,
  useInfiniteQuery,
  useQueryClient,
  useMutation,
} from "react-query";
import qs from "qs";
import { useFetchContext, fetchWithContext } from "../plugins/fetch";
import { useFetch } from "./common";

export function useTask(taskName, defaultTask) {
  let path;
  if (taskName) {
    path = `/metadata/taskdefs/${taskName}`;
  }
  return useFetch(["taskDef", taskName], path, {}, defaultTask);
}

export function useTaskSearch({ searchReady, ...searchObj }) {
  const fetchContext = useFetchContext();
  const queryClient = useQueryClient();

  const pathRoot = "/workflow/search-by-tasks?";
  const key = [fetchContext.stack, pathRoot, searchObj];

  return {
    ...useInfiniteQuery(
      key,
      ({ pageParam = 0 }) => {
        const { rowsPerPage, sort, freeText, query } = searchObj;

        if (!searchReady) {
          console.log("blank query - returning empty result.");
          return Promise.resolve({ results: [] });
        }

        const path =
          pathRoot +
          qs.stringify({
            start: rowsPerPage * pageParam,
            size: rowsPerPage,
            sort: sort,
            freeText: freeText,
            query: query,
          });
        return fetchWithContext(path, fetchContext);
      },
      {
        getNextPageParam: (lastPage, pages) => pages.length,
      }
    ),
    refetch: () => {
      queryClient.refetchQueries(key);
    },
  };
}

export function useTaskQueueInfo(taskName) {
  const fetchContext = useFetchContext();

  const pollDataPath = `/tasks/queue/polldata?taskType=${taskName}`;
  const sizePath = `/tasks/queue/sizes?taskType=${taskName}`;

  const { data: pollData, isFetching: pollDataFetching } = useQuery(
    [fetchContext.stack, pollDataPath],
    () => fetchWithContext(pollDataPath, fetchContext),
    {
      enabled: fetchContext.ready && !_.isEmpty(taskName),
    }
  );
  const { data: size, isFetching: sizeFetching } = useQuery(
    [fetchContext.stack, sizePath],
    () => fetchWithContext(sizePath, fetchContext),
    {
      enabled: fetchContext.ready && !_.isEmpty(taskName),
    }
  );

  const taskQueueInfo = useMemo(
    () => ({ size: _.get(size, [taskName]), pollData: pollData }),
    [taskName, pollData, size]
  );

  return {
    data: taskQueueInfo,
    isFetching: pollDataFetching || sizeFetching,
  };
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
