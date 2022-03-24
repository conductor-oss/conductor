import _ from "lodash";
import { useQuery, useMutation } from "react-query";
import { useFetchContext, fetchWithContext } from "../plugins/fetch";

export function useFetch(path, reactQueryOptions, defaultResponse) {
  const fetchContext = useFetchContext();
  return useQuery(
    [fetchContext.stack, path],
    () => {
      if (path) {
        return fetchWithContext(path, fetchContext);
      } else {
        return Promise.resolve(defaultResponse);
      }
    },
    {
      enabled: fetchContext.ready,
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
