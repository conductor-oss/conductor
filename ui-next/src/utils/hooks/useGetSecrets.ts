import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useQuery } from "react-query";
import { SecretDTO } from "types/Secret";
import { STALE_TIME_SEARCH, useAuthHeaders } from "utils/query";

const SECRETS_PATH = "/secrets-v2";

export const useGetSecrets = () => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };

  return useQuery<SecretDTO[]>(
    [fetchContext.stack, SECRETS_PATH],
    () => {
      const path = SECRETS_PATH;
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
