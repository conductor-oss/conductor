import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useAuthHeaders, STALE_TIME_SEARCH } from "utils/query";
import { useQuery } from "react-query";
import { User } from "types";

const USERS_PATH = "/users";

export const useGetUsers = () => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };

  return useQuery<User[]>(
    [fetchContext.stack, USERS_PATH, {}],
    () => {
      const path = USERS_PATH;
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
