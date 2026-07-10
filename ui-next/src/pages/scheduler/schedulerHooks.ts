import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useCallback } from "react";
import {
  useMutation,
  UseMutationOptions,
  UseMutationResult,
  useQueryClient,
} from "react-query";
import { IScheduleDto } from "types/Schedulers";
import { useAuthHeaders, useFetch } from "../../utils/query";

export function useSchedule(name: string | null | undefined) {
  return useFetch<IScheduleDto>(`/scheduler/schedules/${name}`, {
    enabled: !!name,
  });
}

/**
 * Returns an async function that checks whether a schedule with the given name
 * already exists. Resolves to `true` if it does, `false` on 404.
 * Used by the clone dialog to detect duplicates at submit time without loading
 * a full schedule list on page mount.
 */
export function useCheckScheduleExists() {
  const fetchContext = useFetchContext();
  const authHeaders = useAuthHeaders();

  return useCallback(
    async (name: string): Promise<boolean> => {
      try {
        const result = await fetchWithContext(
          `/scheduler/schedules/${encodeURIComponent(name)}`,
          fetchContext,
          { headers: authHeaders },
        );
        return result != null;
      } catch (err: unknown) {
        if ((err as Response)?.status === 404) return false;
        throw err;
      }
    },
    [fetchContext, authHeaders],
  );
}

export interface SaveScheduleVariables {
  body: string;
  overwrite?: boolean;
}

export type UseSaveScheduleOptions = Omit<
  UseMutationOptions<void, Response, SaveScheduleVariables>,
  "mutationFn"
>;

export function useSaveSchedule({
  onSuccess,
  ...callbacks
}: UseSaveScheduleOptions = {}): UseMutationResult<
  void,
  Response,
  SaveScheduleVariables
> {
  const queryClient = useQueryClient();
  const fetchContext = useFetchContext();
  const authHeaders = useAuthHeaders();

  return useMutation<void, Response, SaveScheduleVariables>(
    (mutateParams) => {
      const path = mutateParams.overwrite
        ? "/scheduler/schedules?overwrite=true"
        : "/scheduler/schedules";
      return fetchWithContext(path, fetchContext, {
        method: "post",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body: mutateParams.body,
      });
    },
    {
      onSuccess: (data, mutationVariables, context) => {
        queryClient.invalidateQueries();
        onSuccess?.(data, mutationVariables, context);
      },
      ...callbacks,
    },
  );
}
