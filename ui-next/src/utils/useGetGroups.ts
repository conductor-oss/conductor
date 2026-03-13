import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useQuery } from "react-query";
import { AccessGroup } from "types";
import { STALE_TIME_SEARCH, useAuthHeaders } from "utils/query";

const GROUPS_PATH = "/groups";

export const useGetGroups = () => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };

  return useQuery<AccessGroup[]>(
    [fetchContext.stack, GROUPS_PATH, {}],
    () => {
      const path = GROUPS_PATH;
      return fetchWithContext(path, fetchContext, fetchParams);
      // staletime to ensure stable view when paginating back and forth (even if underlying results change)
    },
    {
      enabled: fetchContext.ready,
      keepPreviousData: true,
      staleTime: STALE_TIME_SEARCH,
      retry: (failureCount: number, error: any) => {
        if (error?.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount > 3;
      },
    },
  );
};
