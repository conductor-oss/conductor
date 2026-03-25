import { useQuery } from "react-query";
import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useAuthHeaders } from "utils/query";

export interface TagResourceItem {
  id: string;
  displayName: string;
}

export const useTagResources = (
  tagKey: string | null,
  tagValue: string | null,
  resourceType: string | null,
) => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };

  return useQuery(
    [fetchContext.stack, "tag-resources", tagKey, tagValue, resourceType],
    async () => {
      const params = new URLSearchParams({
        tagKey: tagKey!,
        tagValue: tagValue!,
        resourceType: resourceType!,
      });
      const result = await fetchWithContext(
        `/metadata/tags/resources?${params.toString()}`,
        fetchContext,
        fetchParams,
      );
      return (result as TagResourceItem[]) || [];
    },
    {
      enabled:
        fetchContext.ready &&
        tagKey != null &&
        tagValue != null &&
        resourceType != null,
      staleTime: 30000,
      retry: (failureCount, error: unknown) => {
        const status = (error as { status?: number })?.status;
        if (status && status >= 400 && status < 500) {
          return false;
        }
        return failureCount < 3;
      },
    },
  );
};
