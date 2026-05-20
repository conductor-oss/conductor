import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { FEATURES, featureFlags } from "utils/flags";
import { toMaybeQueryString } from "utils";
import {
  computeFetchBaseEnabled,
  DEFAULT_STALE_TIME,
  STALE_TIME_WORKFLOW_DEFS,
  useAuthHeaders,
} from "utils/query";
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
  const headers = useAuthHeaders();
  const fetchParams = { headers };
  const props = { activeOnly, ...restProps };

  return useQuery<IntegrationI[]>(
    [fetchContext.stack, INTEGRATIONS_PATH, props],
    () => {
      const path = `${INTEGRATIONS_PATH}${toMaybeQueryString(props)}`;
      return fetchWithContext(path, fetchContext, fetchParams);
      // staletime to ensure stable view when paginating back and forth (even if underlying results change)
    },
    {
      enabled:
        computeFetchBaseEnabled(fetchContext, headers) &&
        featureFlags.isEnabled(FEATURES.INTEGRATIONS),
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
  const headers = useAuthHeaders();
  const fetchParams = { headers };
  return useQuery<IntegrationDef[]>(
    [fetchContext.stack, INTEGRATION_DEF_PATH],
    () => {
      const path = `${INTEGRATION_DEF_PATH}`;
      return fetchWithContext(path, fetchContext, fetchParams);
      // staletime to ensure stable view when paginating back and forth (even if underlying results change)
    },
    {
      enabled:
        computeFetchBaseEnabled(fetchContext, headers) &&
        featureFlags.isEnabled(FEATURES.INTEGRATIONS),
      staleTime: STALE_TIME_WORKFLOW_DEFS,
    },
  );
};
