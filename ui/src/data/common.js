import _ from "lodash";
import { useQuery, useQueries, useMutation } from "react-query";
import useAppContext from "../hooks/useAppContext";

export function useFetchParallel(paths, reactQueryOptions) {
  const {fetchWithContext, ready, stack} = useAppContext();

  return useQueries(
    paths.map((path) => {
      return {
        queryKey: [stack, ...path],
        queryFn: () => fetchWithContext(`/${path.join("/")}`),
        enabled:
          ready && _.get(reactQueryOptions, "enabled", true),
        keepPreviousData: true,
        ...reactQueryOptions,
      };
    })
  );
}

export function useFetch(key, path, reactQueryOptions, defaultResponse) {
  const {fetchWithContext, ready, stack } = useAppContext();

  return useQuery(
    [stack, ...key],
    () => {
      if (path) {
        return fetchWithContext(path);
      } else {
        return Promise.resolve(defaultResponse);
      }
    },
    {
      enabled: ready && _.get(reactQueryOptions, "enabled", true),
      keepPreviousData: true,
      ...reactQueryOptions,
    }
  );
}

export function useAction(path, method = "post", callbacks) {
  const {fetchWithContext} = useAppContext();
  return useMutation(
    (mutateParams) =>
      fetchWithContext(path, {
        method,
        headers: {
          "Content-Type": "application/json",
        },
        body: _.get(mutateParams, "body"),
      }),
    callbacks
  );
}
