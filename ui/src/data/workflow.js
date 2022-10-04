import { useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "react-query";
import useAppContext from "../hooks/useAppContext";
import { useFetch, useFetchParallel } from "./common";
import qs from "qs";

const STALE_TIME_WORKFLOW_DEFS = 600000; // 10 mins
const STALE_TIME_SEARCH = 60000; // 1 min

export function useWorkflowSearch(searchObj) {
  const { fetchWithContext, ready, stack } = useAppContext();

  const pathRoot = "/workflow/search?";

  return useQuery(
    [stack, pathRoot, searchObj],
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
      return fetchWithContext(path);
      // staletime to ensure stable view when paginating back and forth (even if underlying results change)
    },
    {
      enabled: ready,
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
  const { stack } = useAppContext();
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

export function useWorkflowNamesAndVersions() {
  return useFetch(
    ["workflowNamesAndVersions"],
    "/metadata/workflow/names-and-versions",
    {
      staleTime: STALE_TIME_WORKFLOW_DEFS,
    }
  );
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
  const { fetchWithContext } = useAppContext();

  return useMutation(
    ({ body, isNew }) =>
      fetchWithContext(path, {
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
  const { data } = useWorkflowNamesAndVersions();
  // Extract unique names
  return useMemo(() => {
    if (data) {
      return Object.keys(data).sort();
    } else {
      return [];
    }
  }, [data]);
}

export function useStartWorkflow(callbacks) {
  const path = "/workflow";
  const { fetchWithContext } = useAppContext();

  return useMutation(
    ({ body }) =>
      fetchWithContext(path, {
        method: "post",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      }),
    callbacks
  );
}
