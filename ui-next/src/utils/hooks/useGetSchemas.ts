import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useQuery } from "react-query";
import { SchemaDefinition } from "types/SchemaDefinition";
import { DEFAULT_STALE_TIME, useAuthHeaders } from "utils/query";

const SCHEMAS_PATH = "/schema";
/**
 * Tags come back only when asked for: the list is also read by clients that deserialise it
 * into a type with no tags field and reject anything unexpected. The OSS server has no tag
 * system and ignores the parameter.
 */
const SCHEMAS_LIST_PATH = `${SCHEMAS_PATH}?metadata=true`;

export const useGetSchemas = () => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };

  return useQuery<SchemaDefinition[]>(
    [fetchContext.stack, SCHEMAS_LIST_PATH, {}],
    () => {
      const path = SCHEMAS_LIST_PATH;
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
