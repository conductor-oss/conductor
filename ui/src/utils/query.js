import _ from "lodash";
import { useMemo } from "react";
import {
  useQuery,
  useMutation,
  useInfiniteQuery,
  useQueryClient,
} from "react-query";
import qs from "qs";
import { useFetchContext, fetchWithContext } from "../plugins/fetch";

const STALE_TIME_DROPDOWN = 600000; // 10 mins
const STALE_TIME_WORKFLOW_DEFS = 600000; // 10 mins
const STALE_TIME_SEARCH = 60000; // 1 min

export function useFetch(path, reactQueryOptions) {
  const fetchContext = useFetchContext();

  return useQuery(
    [fetchContext.stack, path],
    () => fetchWithContext(path, fetchContext),
    {
      enabled: fetchContext.ready,
      keepPreviousData: true,
      ...reactQueryOptions,
    }
  );
}
export function useWorkflowSearch(searchObj) {
  const fetchContext = useFetchContext();
  const pathRoot = "/workflow/search?";

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
      // staletime to ensure stable view when paginating back and forth (even if underlying results change)
    },
    {
      enabled: fetchContext.ready,
      keepPreviousData: true,
      staleTime: STALE_TIME_SEARCH,
    }
  );
}

export function useTaskSearch(searchObj) {
  const fetchContext = useFetchContext();
  const queryClient = useQueryClient();

  const pathRoot = "/workflow/search-by-tasks?";
  const key = [fetchContext.stack, pathRoot, searchObj];

  return {
    ...useInfiniteQuery(
      key,
      ({ pageParam = 0 }) => {
        const { rowsPerPage, sort, freeText, query } = searchObj;

        if (query === "") {
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

export function useAction(path, method = "post", callbacks) {
  const fetchContext = useFetchContext();
  return useMutation(
    (mutateParams) =>
      fetchWithContext(path, fetchContext, {
        method,
        headers: {
          "Content-Type": "application/json",
        },
        body: _.get(mutateParams, "body"),
      }),
    callbacks
  );
}

export function useWorkflowDefs() {
  const { data, ...rest } = useFetch("/metadata/workflow", {
    staleTime: STALE_TIME_WORKFLOW_DEFS,
  });

  // Filter latest versions only
  const workflows = useMemo(() => {
    if (data) {
      const unique = new Map();
      const types = new Set();
      for (let workflowDef of data) {
        if (!unique.has(workflowDef.name)) {
          unique.set(workflowDef.name, workflowDef);
        } else if (unique.get(workflowDef.name).version < workflowDef.version) {
          unique.set(workflowDef.name, workflowDef);
        }

        for (let task of workflowDef.tasks) {
          types.add(task.type);
        }
      }

      return Array.from(unique.values());
    }
  }, [data]);

  return {
    data: workflows,
    ...rest,
  };
}

export function useWorkflowNames() {
  const { data } = useWorkflowDefs();
  return useMemo(() => (data ? data.map((def) => def.name) : []), [data]);
}

// Version numbers do not necessarily start, or run contiguously from 1. Could arbitrary integers e.g. 52335678.
// By convention they should be monotonic (ever increasing) wrt time.
export function useWorkflowNamesAndVersions() {
  const { data } = useFetch("/metadata/workflow", {
    staleTime: STALE_TIME_WORKFLOW_DEFS,
  });

  return useMemo(() => {
    const retval = new Map();
    if (data) {
      for (let def of data) {
        let arr;
        if (!retval.has(def.name)) {
          arr = [];
          retval.set(def.name, arr);
        } else {
          arr = retval.get(def.name);
        }
        arr.push(def.version);
      }

      // Sort arrays in place
      retval.forEach((val) => val.sort());
    }
    return retval;
  }, [data]);
}

export function useTaskNames() {
  const { data } = useFetch(`/metadata/taskdefs`, {
    staleTime: STALE_TIME_DROPDOWN,
  });
  return useMemo(
    () => (data ? Array.from(new Set(data.map((def) => def.name))).sort() : []),
    [data]
  );
}
