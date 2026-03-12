import { fetchWithContext, useFetchContext } from "plugins/fetch";
import {
  useAuthHeaders,
  STALE_TIME_WORKFLOW_DEFS,
  toMaybeQueryString,
  DEFAULT_STALE_TIME,
} from "utils";
import { useQuery } from "react-query";
import { IntegrationDef, IntegrationI } from "types";

const INTEGRATIONS_PATH = "/integrations/provider";
const INTEGRATION_DEF_PATH = "/integrations/def";

type GetIntegrationsProps = {
  category?: string;
  activeOnly?: boolean;
};

export const useGetIntegration = ({
  activeOnly = false,
  ...restProps
}: GetIntegrationsProps) => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };
  const props = { activeOnly, ...restProps };

  return useQuery<IntegrationI[]>(
    [fetchContext.stack, INTEGRATIONS_PATH, props],
    () => {
      const path = `${INTEGRATIONS_PATH}${toMaybeQueryString(props)}`;
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

export const useGetIntegrationDef = () => {
  const fetchContext = useFetchContext();
  const fetchParams = { headers: useAuthHeaders() };
  return useQuery<IntegrationDef[]>(
    [fetchContext.stack, INTEGRATION_DEF_PATH],
    () => {
      const path = `${INTEGRATION_DEF_PATH}`;
      return fetchWithContext(path, fetchContext, fetchParams);
      // staletime to ensure stable view when paginating back and forth (even if underlying results change)
    },
    {
      enabled: fetchContext.ready,
      staleTime: STALE_TIME_WORKFLOW_DEFS,
    },
  );
};
