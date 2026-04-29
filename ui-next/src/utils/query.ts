import _get from "lodash/get";
import _isEmpty from "lodash/isEmpty";
import _sortBy from "lodash/sortBy";
import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { pluginRegistry } from "plugins/registry";
import qs from "qs";
import { useMemo } from "react";
import {
  useInfiniteQuery,
  useMutation,
  UseMutationOptions,
  UseMutationResult,
  useQuery,
  useQueryClient,
  UseQueryOptions,
  UseQueryResult,
} from "react-query";
import { useLocation, useNavigate } from "react-router";
import { getAccessToken as getAccessTokenStub } from "components/features/auth/tokenManagerJotai";

// Get access token from plugin registry (enterprise) or fallback to stub (OSS)
function getAccessToken(): string | null {
  // Try plugin registry first (enterprise)
  const pluginToken = pluginRegistry.getAccessToken();
  if (pluginToken) {
    return pluginToken;
  }
  // Fallback to stub (OSS - always returns null)
  return getAccessTokenStub();
}
import { WorkflowDef } from "types/WorkflowDef";
import { AuthHeaders, IObject } from "types/common";
import {
  getUniqueWorkflows,
  getUniqueWorkflowsWithVersions,
} from "utils/workflow";
import {
  TASK_EXECUTIONS_SEARCH_URL,
  WORKFLOW_METADATA_SHORT_URL,
} from "./constants/api";
import { HttpStatusCode } from "./constants/httpStatusCode";
import { ERROR_URL } from "./constants/route";
import { featureFlags, FEATURES } from "./flags";
import { logger } from "./logger";

// Type definitions
export interface SearchObj {
  rowsPerPage: number;
  page: number;
  sort?: string;
  freeText?: string;
  query?: string;
  queryId?: string;
}

export interface TaskSearchObj extends Omit<SearchObj, "page"> {
  searchReady?: boolean;
}

export interface SearchResult<T = any> {
  results: T[];
  totalHits: number;
}

export interface PollData {
  workerId?: string;
  domain?: string;
  lastPollTime?: number;
}

export interface TaskQueueInfo {
  size?: number;
  pollData?: PollData[];
}

export interface QueueInfo {
  name: string;
  size: number;
}

// Keep MutateParams flexible to allow any additional properties
export interface MutateParams {
  path?: string;
  method?: string;
  body?: any;
  [key: string]: any;
}

// FetchError is any Response-like object with status - kept permissive for backward compatibility
type FetchError = any;

/** Options for {@link useFetch} — extends react-query options with enterprise API gating. */
export type UseFetchQueryOptions<T = unknown> = Partial<
  UseQueryOptions<T, FetchError>
> & {
  /** When set, request runs only if this feature flag is on (AND with normal fetch/auth readiness). */
  enterpriseApiFeature?: string;
  /** AND with the resolved enabled state (e.g. `Boolean(id)` for keyed routes). Default true. */
  when?: boolean;
};

// Constants
export const STALE_TIME_DROPDOWN = 600000; // 10 mins
export const STALE_TIME_WORKFLOW_DEFS = 600000; // 10 mins
export const STALE_TIME_SECRET_NAMES = 60000; // 1 min
export const STALE_TIME_SEARCH = 60000; // 1 min
export const DEFAULT_STALE_TIME = 5000; // 5 Seconds
export const AUTH_HEADER_NAME = "X-Authorization";

/** Same predicate as the default `enabled` option inside {@link useFetch} (fetch ready + auth rules). */
export function computeFetchBaseEnabled(
  fetchContext: { ready: boolean },
  headers: AuthHeaders,
): boolean {
  return (
    fetchContext.ready &&
    (headers[AUTH_HEADER_NAME] !== undefined ||
      !featureFlags.isEnabled(FEATURES.ACCESS_MANAGEMENT))
  );
}

export function useFetch<T = any>(
  path: string,
  reactQueryOptions?: UseFetchQueryOptions<T>,
  fetchOptions: IObject = {},
  optionalKey?: string,
): UseQueryResult<T, FetchError> {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };
  const navigate = useNavigate();
  const location = useLocation();
  const {
    enterpriseApiFeature,
    when = true,
    enabled: enabledOption,
    ...queryOpts
  } = reactQueryOptions ?? {};

  const baseEnabled = computeFetchBaseEnabled(
    fetchContext,
    fetchParams.headers,
  );
  const featureGated =
    enterpriseApiFeature != null
      ? baseEnabled && featureFlags.isEnabled(enterpriseApiFeature)
      : baseEnabled;
  const mergedEnabled = featureGated && when;
  const resolvedEnabled =
    enabledOption !== undefined ? enabledOption : mergedEnabled;

  const query = useQuery<T, FetchError>(
    optionalKey == null
      ? [fetchContext.stack, path]
      : [fetchContext.stack, path, optionalKey],
    () =>
      fetchWithContext(path, fetchContext, { ...fetchParams, ...fetchOptions }),
    {
      // In OSS mode (ACCESS_MANAGEMENT disabled), always enabled when fetchContext is ready
      keepPreviousData: true,
      retry: (failureCount: number, error: FetchError) => {
        // Don't retry on 403 or 401
        if (error?.status === 403 || error?.status === 401) return false;
        return failureCount < 3;
      },
      ...queryOpts,
      enabled: resolvedEnabled,
    },
  );

  const statusCode = query?.error?.status;

  // Handle 401 errors by navigating to error page
  // In OSS mode, 401 errors shouldn't normally occur since there's no authentication
  if (query.isError && statusCode === HttpStatusCode.Unauthorized) {
    try {
      // Skip navigation for apigateway paths
      if (path.startsWith("/gateway")) {
        return query;
      }

      logger.warn("[useFetch] 401 error, navigating to error page");

      query.error
        ?.clone()
        ?.json()
        ?.then((result: any) => {
          const params = [`code=${statusCode}`];
          if (result?.message) params.push(`message=${result.message}`);
          if (result?.error) params.push(`error=${result.error}`);

          if (location.pathname !== ERROR_URL) {
            navigate(`${ERROR_URL}?${params.join("&")}`);
          }
        });
    } catch (error) {
      logger.error("[useFetch] error: ", error);
    }
  }

  return query;
}

export function useAuthHeaders(): AuthHeaders {
  const accessToken = getAccessToken();
  if (accessToken) {
    return { [AUTH_HEADER_NAME]: accessToken };
  }

  return {};
}

export function useWorkflowSearch<T = any>(
  searchObj: SearchObj,
  queryOption: Partial<UseQueryOptions<T, FetchError>> = {},
  queryOptionOverride: Partial<UseQueryOptions<T, FetchError>> = {},
): UseQueryResult<T, FetchError> {
  return useSearch<T>(
    searchObj,
    "/workflow/search?",
    queryOption,
    queryOptionOverride,
  );
}

export function useSchedulerSearch<T = any>(
  searchObj: SearchObj,
): UseQueryResult<T, FetchError> {
  return useSearch<T>(searchObj, "/scheduler/search/executions?");
}

export function useTaskExecutionsSearch<T = any>(
  searchObj: SearchObj,
  queryOptionOverride: Partial<UseQueryOptions<T, FetchError>> = {},
): UseQueryResult<T, FetchError> {
  return useSearch<T>(
    searchObj,
    TASK_EXECUTIONS_SEARCH_URL,
    {},
    queryOptionOverride,
  );
}

export function useSearch<T = any>(
  searchObj: SearchObj,
  pathRoot: string,
  queryOption: Partial<UseQueryOptions<T, FetchError>> = {},
  queryOptionsOverride: Partial<UseQueryOptions<T, FetchError>> = {},
): UseQueryResult<T, FetchError> {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };

  return useQuery<T, FetchError>(
    [fetchContext.stack, pathRoot, searchObj],
    () => {
      const { rowsPerPage, page, sort, freeText, query } = searchObj;
      let params: IObject = {
        start: (page - 1) * rowsPerPage,
        size: rowsPerPage,
        sort: sort,
        freeText: freeText,
        query: query,
      };
      if (searchObj.queryId) {
        params = { queryId: searchObj.queryId, ...params };
      }
      const path = pathRoot + qs.stringify(params);
      return fetchWithContext(path, fetchContext, fetchParams);
      // staletime to ensure stable view when paginating back and forth (even if underlying results change)
    },
    {
      enabled:
        typeof queryOption.enabled === "boolean"
          ? queryOption.enabled
          : fetchContext.ready,
      keepPreviousData: true,
      staleTime: STALE_TIME_SEARCH,
      retry: (failureCount: number, error: FetchError) => {
        if (error?.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount - 2 > 0;
      },
      ...queryOptionsOverride,
    },
  );
}

// @Deprecated
export function useTaskSearch({
  searchReady,
  ...searchObj
}: TaskSearchObj & { searchReady?: boolean }) {
  const fetchContext = useFetchContext();
  const queryClient = useQueryClient();
  const fetchParams = { headers: useAuthHeaders() };

  const pathRoot = "/workflow/search-by-tasks?";
  const key = [fetchContext.stack, pathRoot, searchObj];

  const infiniteQuery = useInfiniteQuery<any, FetchError>(
    key,
    ({ pageParam = 0 }) => {
      const { rowsPerPage, sort, freeText, query } = searchObj;

      if (!searchReady) {
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
      return fetchWithContext(path, fetchContext, fetchParams);
    },
    {
      getNextPageParam: (_lastPage, pages) => pages.length,
    },
  );

  return {
    ...infiniteQuery,
    refetch: () => {
      queryClient.refetchQueries(key);
    },
  };
}

export function useTaskQueueInfo(taskName: string): {
  data: TaskQueueInfo;
  isFetching: boolean;
} {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };

  const pollDataPath = `/tasks/queue/polldata?taskType=${taskName}`;
  const sizePath = `/tasks/queue/sizes?taskType=${taskName}`;

  const { data: pollData, isFetching: pollDataFetching } = useQuery<
    PollData[],
    FetchError
  >(
    [fetchContext.stack, pollDataPath],
    () => fetchWithContext(pollDataPath, fetchContext, fetchParams),
    {
      enabled: fetchContext.ready && !_isEmpty(taskName),
    },
  );
  const { data: size, isFetching: sizeFetching } = useQuery<
    Record<string, number>,
    FetchError
  >(
    [fetchContext.stack, sizePath],
    () => fetchWithContext(sizePath, fetchContext, fetchParams),
    {
      enabled: fetchContext.ready && !_isEmpty(taskName),
    },
  );

  const taskQueueInfo = useMemo(
    () => ({ size: _get(size, [taskName]), pollData: pollData }),
    [taskName, pollData, size],
  );

  return {
    data: taskQueueInfo,
    isFetching: pollDataFetching || sizeFetching,
  };
}

export function useAction(
  path: string,
  method = "post",
  callbacks?: any,
  isText?: boolean,
) {
  const fetchContext = useFetchContext();
  const authHeaders = useAuthHeaders();

  return useMutation(
    (mutateParams) =>
      fetchWithContext(
        path,
        fetchContext,
        {
          method,
          headers: {
            "Content-Type": "application/json",
            ...authHeaders,
          },
          body: _get(mutateParams, "body"),
        },
        isText,
      ),
    callbacks,
  );
}

export function useActionWithPath<TData = any>(
  callbacks?: UseMutationOptions<TData, any, any>,
  isText?: boolean,
  throwOnError?: boolean,
): UseMutationResult<TData, any, any> {
  const fetchContext = useFetchContext();
  const authHeaders = useAuthHeaders();
  return useMutation<TData, any, any>((mutateParams) => {
    const actionPath = _get(mutateParams, "path") as string;
    const method = _get(mutateParams, "method") as string;
    const contentType = isText ? "text/plain" : "application/json";
    return fetchWithContext(
      actionPath,
      fetchContext,
      {
        method,
        headers: {
          "Content-Type": contentType,
          ...authHeaders,
        },
        body: _get(mutateParams, "body"),
      },
      isText,
      throwOnError,
    );
  }, callbacks);
}

export function useUsersListing(includeApps = false) {
  const { data, ...rest } = useFetch(`/users?apps=${includeApps}`, {
    staleTime: DEFAULT_STALE_TIME,
  });
  return {
    data: useMemo(() => (data ? data : []), [data]),
    ...rest,
  };
}

export function useWorkflowDefs(
  optionsOverride: Partial<UseQueryOptions<WorkflowDef[], FetchError>> = {},
): UseQueryResult<WorkflowDef[], FetchError> {
  return useFetch<WorkflowDef[]>(WORKFLOW_METADATA_SHORT_URL, {
    staleTime: DEFAULT_STALE_TIME,
    ...optionsOverride,
  });
}

export function useWorkflowNames(
  optionsOverride: Partial<UseQueryOptions<WorkflowDef[], FetchError>> = {},
): string[] {
  const { data } = useWorkflowDefs(optionsOverride);

  // Filter latest versions only
  const workflows = useMemo(() => {
    if (data) {
      return getUniqueWorkflows(data);
    }
  }, [data]);

  return useMemo(
    () => (workflows ? workflows.map((def) => def.name) : []),
    [workflows],
  );
}

export const useSharedQueryContext = (): {
  url: string;
  cacheQueryKey: (string | undefined)[];
  fetchContext: ReturnType<typeof useFetchContext>;
} => {
  const fetchContext = useFetchContext();
  const url = WORKFLOW_METADATA_SHORT_URL;
  const cacheQueryKey = [fetchContext.stack, url];
  return { url, cacheQueryKey: cacheQueryKey, fetchContext };
};

export const usePrefetchWorkflows = (): void => {
  const headers = useAuthHeaders();
  const { url, fetchContext, cacheQueryKey } = useSharedQueryContext();
  const queryClient = useQueryClient();

  // In OSS mode, always prefetch (no authentication check needed)
  const fetchParams = { headers };
  queryClient.prefetchQuery({
    queryKey: cacheQueryKey,
    queryFn: () => fetchWithContext(url, fetchContext, fetchParams),
    staleTime: STALE_TIME_WORKFLOW_DEFS,
  });
};

// Version numbers do not necessarily start, or run contiguously from 1. Could arbitrary integers e.g. 52335678.
// By convention they should be monotonic (ever increasing) wrt time.
// @Deprecated use useWorkflowNamesAndVersionsQuery instead
export function useWorkflowNamesAndVersions(): Map<string, number[]> {
  const { url } = useSharedQueryContext();
  const { data } = useFetch<WorkflowDef[]>(url, {
    staleTime: STALE_TIME_WORKFLOW_DEFS,
  });

  return useMemo(() => getUniqueWorkflowsWithVersions(data), [data]);
}

export function useWorkflowDefsByVersions({
  queryParams = {},
}: { queryParams?: IObject | string } = {}) {
  const queryString =
    typeof queryParams === "object" && !Array.isArray(queryParams)
      ? qs.stringify(queryParams, { addQueryPrefix: true })
      : queryParams;

  const { data } = useFetch(`/metadata/workflow${queryString}`, {
    staleTime: STALE_TIME_WORKFLOW_DEFS,
  });

  return useMemo(() => {
    const retval = new Map();
    const lookups = new Map();
    const values = new Map();
    if (data) {
      for (const def of data) {
        let lArr: any[];
        let vMap: Map<string, any>;
        if (!lookups.has(def.name)) {
          lArr = [];
          vMap = new Map();
          lookups.set(def.name, lArr);
          values.set(def.name, vMap);
        } else {
          lArr = lookups.get(def.name);
          vMap = values.get(def.name);
        }
        lArr.push(def.version.toString()); // Someone will eventually come back to this.
        vMap.set(def.version.toString(), def);
      }

      // Sort arrays in place
      lookups.forEach((val, key) => {
        // Sort versions
        lookups.set(
          key,
          _sortBy(val, (val) => Number(val)),
        );
      });
    }

    retval.set("lookups", lookups);
    retval.set("values", values);

    return retval;
  }, [data]);
}

export function useTaskNames(access?: string) {
  const queryParams = access ? `?access=${access}` : "";
  const { data } = useFetch(`/metadata/taskdefs${queryParams}`, {
    staleTime: STALE_TIME_DROPDOWN,
  });
  return useMemo(
    () =>
      data ? Array.from(new Set(data.map((def: any) => def.name))).sort() : [],
    [data],
  );
}

export function useGroupsListing() {
  const { data, ...rest } = useFetch("/groups", {
    staleTime: DEFAULT_STALE_TIME,
  });
  return {
    data: useMemo(() => (data ? data : []), [data]),
    ...rest,
  };
}

export function useRolesListing() {
  const { data, ...rest } = useFetch("/roles", {
    staleTime: DEFAULT_STALE_TIME,
  });
  return {
    data: useMemo(() => (data ? data : []), [data]),
    ...rest,
  };
}

export function useCustomRolesListing() {
  const { data, ...rest } = useFetch("/roles/custom", {
    staleTime: DEFAULT_STALE_TIME,
  });
  return {
    data: useMemo(() => (data ? data : []), [data]),
    ...rest,
  };
}

export function useSystemRoles() {
  const { data, ...rest } = useFetch("/roles/system", {
    staleTime: DEFAULT_STALE_TIME,
  });
  return {
    data: useMemo(() => (data ? data : {}), [data]),
    ...rest,
  };
}

export function useAvailablePermissions() {
  const { data, ...rest } = useFetch("/roles/permissions", {
    staleTime: DEFAULT_STALE_TIME,
  });
  return {
    data: useMemo(() => (data?.permissions ? data.permissions : []), [data]),
    ...rest,
  };
}

export function useSingleRole(roleId?: string) {
  const { data, ...rest } = useFetch(`/roles/${roleId}`, {
    staleTime: DEFAULT_STALE_TIME,
    enabled: !!roleId,
  });
  return {
    data: useMemo(() => data, [data]),
    ...rest,
  };
}

export function useGroupUsers(id: string) {
  const { data, ...rest } = useFetch(`/groups/${id}/users`, {
    staleTime: DEFAULT_STALE_TIME,
  });
  return {
    data: useMemo(() => (data ? data : []), [data]),
    ...rest,
  };
}

export function useUserById(id: string) {
  const { data, ...rest } = useFetch(`/users/${id}`, {
    staleTime: DEFAULT_STALE_TIME,
  });
  return {
    data: data || {},
    ...rest,
  };
}

export function useUserPermissions(id: string) {
  const { data, ...rest } = useFetch(`/users/${id}/permissions`, {
    staleTime: DEFAULT_STALE_TIME,
  });
  return {
    data: useMemo(() => (data ? data : {}), [data]),
    ...rest,
  };
}

//TODO consider adding an API operation to get the Workflow definition names from the backend
export function useWorkflowDefNames(access?: string) {
  const queryParams = access ? `?access=${access}` : "";
  const { data, ...rest } = useFetch<WorkflowDef[]>(
    `/metadata/workflow${queryParams}&short=true`,
    {
      staleTime: STALE_TIME_WORKFLOW_DEFS,
    },
  );

  const extractNames = (defs: WorkflowDef[]): string[] => {
    const names = defs.map((def) => def.name);
    return [...new Set(names)].sort();
  };

  return {
    data: data ? extractNames(data) : [],
    ...rest,
  };
}

export function useSecretNames(): string[] {
  const { data } = useFetch(`/secrets`, {
    staleTime: STALE_TIME_SECRET_NAMES,
  });
  return data ? data : [];
}

export function useAppListing() {
  const { data, ...rest } = useFetch("/applications", {
    staleTime: DEFAULT_STALE_TIME,
    enterpriseApiFeature: FEATURES.ACCESS_MANAGEMENT,
  });
  return {
    data: data || [],
    ...rest,
  };
}

export function useApplicationById(id: string) {
  const { data, ...rest } = useFetch(`/applications/${id}`, {
    staleTime: DEFAULT_STALE_TIME,
    enterpriseApiFeature: FEATURES.ACCESS_MANAGEMENT,
    when: Boolean(id),
  });
  return {
    data: data || {},
    ...rest,
  };
}

export function useAccessKeysListing(applicationId: string) {
  const { data, ...rest } = useFetch(
    `/applications/${applicationId}/accessKeys`,
    {
      staleTime: DEFAULT_STALE_TIME,
      enterpriseApiFeature: FEATURES.ACCESS_MANAGEMENT,
      when: Boolean(applicationId),
    },
  );
  return {
    data: data || [],
    ...rest,
  };
}

export const useCurrentUserInfo = (function () {
  if (featureFlags.isEnabled(FEATURES.ACCESS_MANAGEMENT)) {
    return () => {
      return useFetch(`/token/userInfo`, {
        staleTime: DEFAULT_STALE_TIME,
        retry: false, // Don't retry when fetching token
      });
    };
  } else {
    // if access management is not enabled then just return data: {}
    return () => {
      return { data: {}, isFetching: false, isError: false } as const;
    };
  }
})();

export const useAPIReleaseVersion = ({
  keys = [],
  option,
}: {
  keys?: string[];
  option?: any;
} = {}) => {
  return useQuery(
    keys,
    () => fetchWithContext("/version", null as any, null as any, true),
    {
      staleTime: Infinity,
      retry: (failureCount: number, error: any) => {
        if (error?.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount - 2 > 0;
      },
      onSuccess: (data: string) => {
        localStorage.setItem("version", data);
      },
      ...option,
    },
  );
};

export function useTags<T = unknown>(
  reactQueryOptions?: Partial<UseQueryOptions<T, FetchError>>,
) {
  const { data, ...rest } = useFetch<T>("/metadata/tags", {
    staleTime: DEFAULT_STALE_TIME,
    ...reactQueryOptions,
  });

  return {
    data,
    ...rest,
  };
}

export function useQueueDepth() {
  const { data, ...rest } = useFetch("/tasks/queue/all", {
    staleTime: DEFAULT_STALE_TIME,
  });
  const myData: QueueInfo[] = [];
  for (const i in data) {
    const queueInfo = { name: i, size: data[i] };
    myData.push(queueInfo);
  }

  // fitering internal queues
  const filteredData = myData.filter((el) => !el.name.startsWith("_"));

  const sortedData = filteredData.sort((a, b) => b.size - a.size);
  return {
    data: sortedData,
    ...rest,
  };
}
