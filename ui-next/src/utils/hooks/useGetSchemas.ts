import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useQuery } from "react-query";
import { SchemaDefinition } from "types/SchemaDefinition";
import { DEFAULT_STALE_TIME, useAuthHeaders } from "utils/query";

const SCHEMAS_PATH = "/schema";

export const useGetSchemas = () => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };

  return useQuery<SchemaDefinition[]>(
    [fetchContext.stack, SCHEMAS_PATH, {}],
    () => {
      const path = SCHEMAS_PATH;
      return fetchWithContext(path, fetchContext, fetchParams);
      // staletime to ensure stable view when paginating back and forth (even if underlying results change)
    },
    {
      enabled: fetchContext.ready,
      keepPreviousData: true,
      staleTime: DEFAULT_STALE_TIME,
      retry: (failureCount: number, error: any) => {
        if (error?.status >= 400 && error.status < 500) {
          return false;
        }
        return failureCount > 3;
      },
    },
  );
};
