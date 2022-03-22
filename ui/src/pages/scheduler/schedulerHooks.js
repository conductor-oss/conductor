import { useQuery, useMutation, useQueryClient } from "react-query";
import { useFetchContext, fetchWithContext } from "../../plugins/fetch";

export function useSchedules() {
  const fetchContext = useFetchContext();
  return useQuery(
    ["schedules"],
    () => fetchWithContext(`/schedules`, fetchContext),
    {
      initialData: [],
    }
  );
}

export function useSchedule(name) {
  const fetchContext = useFetchContext();
  return useQuery(
    ["schedule", name],
    () => fetchWithContext(`/schedule/${name}`, fetchContext),
    {
      enabled: !!name,
    }
  );
}

export function useSaveSchedule({ onSuccess, ...callbacks }) {
  const fetchContext = useFetchContext();
  const queryClient = useQueryClient();

  return useMutation(
    (body) =>
      fetchWithContext("/schedule", fetchContext, {
        method: "post",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(body),
      }),
    {
      callbacks,
      onSuccess: (data, mutationVariables) => {
        queryClient.invalidateQueries(["schedule", mutationVariables.name]);
        queryClient.invalidateQueries("schedules");
        if (onSuccess) onSuccess(data, mutationVariables);
      },
    }
  );
}
