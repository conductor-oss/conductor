import { useMemo } from "react";
import { useGetIntegration } from "./useGetIntegrations";
import { MESSAGE_BROKER } from "../constants/event";

export const useEventNameSuggestions = () => {
  const { data: integrations = [] } = useGetIntegration({
    category: MESSAGE_BROKER,
  });

  return useMemo(
    () => integrations.map(({ type, name }) => `${type}:${name}`),
    [integrations],
  );
};
