import { useFetch } from "./common";

export const useEventHandlers = () => {
  return useFetch("/event");
};

export const useLogs = ({ taskId }) => {
  return useFetch(`/tasks/${taskId}/log`);
};
