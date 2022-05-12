import _ from "lodash";
import { useQuery, useQueries, useMutation } from "react-query";
import { useFetchContext, fetchWithContext } from "../plugins/fetch";

export function useFetchParallel(paths, reactQueryOptions) {
  const fetchContext = useFetchContext();

  return useQueries(
    paths.map((path) => {
      return {
        queryKey: [fetchContext.stack, ...path],
        queryFn: () => fetchWithContext(`/${path.join("/")}`, fetchContext),
        enabled:
          fetchContext.ready && _.get(reactQueryOptions, "enabled", true),
        keepPreviousData: true,
        ...reactQueryOptions,
      };
    })
  );
}

export function useFetch(key, path, reactQueryOptions, defaultResponse) {
  const fetchContext = useFetchContext();

  return useQuery(
    [fetchContext.stack, ...key],
    () => {
      if (path) {
        return fetchWithContext(path, fetchContext);
      } else {
        return Promise.resolve(defaultResponse);
      }
    },
    {
      enabled: fetchContext.ready && _.get(reactQueryOptions, "enabled", true),
      keepPreviousData: true,
      ...reactQueryOptions,
    }
  );
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
