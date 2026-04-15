import { useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "react-query";
import { useFetchContext, fetchWithContext } from "../plugins/fetch";
import { useFetch, useFetchParallel } from "./common";
import { useEnv } from "../plugins/env";
import qs from "qs";

const STALE_TIME_WORKFLOW_DEFS = 600000; // 10 mins
const STALE_TIME_SEARCH = 60000; // 1 min

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

export function useWorkflow(workflowId) {
  return useFetch(["workflow", workflowId], `/workflow/${workflowId}`, {
    enabled: !!workflowId,
  });
}

export function useWorkflowsByIds(workflowIds, reactQueryOptions) {
  return useFetchParallel(
    workflowIds.map((workflowId) => ["workflow", workflowId]),
    reactQueryOptions
  );
}

export function useInvalidateWorkflows() {
  const { stack } = useEnv();
  const client = useQueryClient();

  return function (workflowIds) {
    console.log("invalidating workflow Ids", workflowIds);
    client.invalidateQueries({
      predicate: (query) =>
        query.queryKey[0] === stack &&
        query.queryKey[1] === "workflow" &&
        workflowIds.includes(query.queryKey[2]),
    });
  };
}

export function useWorkflowDef(
  workflowName,
  version,
  defaultWorkflow,
  reactQueryOptions = {}
) {
  let path;
  const key = ["workflowDef", workflowName];
  if (workflowName) {
    path = `/metadata/workflow/${workflowName}`;
    if (version) {
      path += `?version=${version}`;
      key.push(version);
    }
  }
  return useFetch(key, path, reactQueryOptions, defaultWorkflow);
}

export function useWorkflowDefs() {
  return useFetch(["workflowDefs"], "/metadata/workflow", {
    staleTime: STALE_TIME_WORKFLOW_DEFS,
  });
}

export function useLatestWorkflowDefs() {
  const { data, ...rest } = useWorkflowDefs();

  // Filter latest versions only
  const workflows = useMemo(() => {
    if (data) {
      const unique = new Map();
      for (let workflowDef of data) {
        if (!unique.has(workflowDef.name)) {
          unique.set(workflowDef.name, workflowDef);
        } else if (unique.get(workflowDef.name).version < workflowDef.version) {
          unique.set(workflowDef.name, workflowDef);
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

export function useSaveWorkflow(callbacks) {
  const path = "/metadata/workflow";
  const fetchContext = useFetchContext();

  return useMutation(
    ({ body, isNew }) =>
      fetchWithContext(path, fetchContext, {
        method: isNew ? "post" : "put",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(isNew ? body : [body]),
      }),
    callbacks
  );
}

export function useWorkflowNames() {
  const { data } = useWorkflowDefs();
  // Extract unique names
  return useMemo(() => {
    if (data) {
      const nameSet = new Set(data.map((def) => def.name));
      return Array.from(nameSet);
    } else {
      return [];
    }
  }, [data]);
}

// Version numbers do not necessarily start, or run contiguously from 1. Could be arbitrary integers e.g. 52335678.
// By convention they should be monotonic (ever increasing) wrt time.
export function useWorkflowNamesAndVersions() {
  const { data, ...rest } = useWorkflowDefs();

  const newData = useMemo(() => {
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
        arr.push({
          version: def.version,
          createTime: def.createTime,
          updateTime: def.updateTime,
        });
      }

      // Sort arrays in place
      retval.forEach((val) => val.sort());
    }
    return retval;
  }, [data]);

  return { ...rest, data: newData };
}

export function useStartWorkflow(callbacks) {
  const path = "/workflow";
  const fetchContext = useFetchContext();

  return useMutation(
    ({ body }) =>
      fetchWithContext(
        path,
        fetchContext,
        {
          method: "post",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(body),
        },
        false
      ),
    callbacks
  );
}
