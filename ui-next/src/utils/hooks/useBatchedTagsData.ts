import { useQuery } from "react-query";
import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useAuthHeaders } from "utils/query";

// Custom hook to fetch grouped tags data
export const useBatchedTagsData = () => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };

  return useQuery(
    [fetchContext.stack, "tags-dashboard-batch"],
    async () => {
      const groupedTags = await fetchWithContext(
        "/metadata/tags/grouped",
        fetchContext,
        fetchParams,
      );

      return groupedTags || [];
    },
    {
      enabled: fetchContext.ready,
      keepPreviousData: true,
      staleTime: 30000, // 30 seconds
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
