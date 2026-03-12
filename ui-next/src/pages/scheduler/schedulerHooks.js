import { useAction, useFetch } from "../../utils/query";
import { useQueryClient } from "react-query";

export function useSchedules() {
  return useFetch("/scheduler/schedules", {
    initialData: [],
  });
}

export function useSchedule(name) {
  return useFetch(`/scheduler/schedules/${name}`, {
    enabled: !!name,
  });
}

export function useSaveSchedule({ onSuccess, ...callbacks }) {
  const queryClient = useQueryClient();

  return useAction("/scheduler/schedules", "post", {
    onSuccess: (data, mutationVariables) => {
      queryClient.invalidateQueries();
      // TODO properly invalidate only the queries scheduler queries
      // queryClient.invalidateQueries(["/scheduler/schedule", mutationVariables.name]);
      // queryClient.invalidateQueries("/scheduler/schedules");
      if (onSuccess) onSuccess(data, mutationVariables);
    },
    ...callbacks,
  });
}
