import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useQuery } from "react-query";
import { EnvironmentVariables } from "types/EnvVariables";
import { STALE_TIME_SEARCH, useAuthHeaders } from "utils/query";

const ENVIRONMENT_VARIABLES_PATH = "/environment";

export const useGetEnvironmentVariables = () => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };

  return useQuery<EnvironmentVariables[]>(
    [fetchContext.stack, ENVIRONMENT_VARIABLES_PATH],
    () => {
      const path = ENVIRONMENT_VARIABLES_PATH;
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
