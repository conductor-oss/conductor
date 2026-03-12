import { toMaybeQueryString } from "./toMaybeQueryString";
import { INTEGRATIONS_API_URL } from "./constants/api";
import { useFetch, STALE_TIME_DROPDOWN } from "./query";
import { IntegrationCategory } from "types/Integrations";

export function useIntegrationProviders({
  category,
  activeOnly,
}: {
  category: IntegrationCategory;
  activeOnly: boolean;
}) {
  const maybeQueryString = toMaybeQueryString({ category, activeOnly });
  const url = `${INTEGRATIONS_API_URL.PROVIDER}${maybeQueryString}`;

  const result = useFetch(url, {
    staleTime: STALE_TIME_DROPDOWN,
  });
  return result;
}
