import { fetchWithContext, useFetchContext } from "plugins/fetch";
import {
  useMutation,
  UseMutationOptions,
  UseMutationResult,
  useQueryClient,
} from "react-query";
import { IScheduleDto } from "types/Schedulers";
import { useAuthHeaders, useFetch } from "../../utils/query";

export function useSchedules() {
  return useFetch<IScheduleDto[]>("/scheduler/schedules", {
    initialData: [],
  });
}

export function useSchedule(name: string | null | undefined) {
  return useFetch<IScheduleDto>(`/scheduler/schedules/${name}`, {
    enabled: !!name,
  });
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
