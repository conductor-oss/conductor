import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useQueries } from "react-query";
import {
  IntegrationCategory,
  IntegrationI,
  ModelDto,
} from "types/Integrations";
import { STALE_TIME_DROPDOWN, useAuthHeaders, useFetch } from "utils/query";

/**
 * Discovers configured AI model integrations and returns them as sorted
 * `"providerName/modelApi"` strings for agent model-override pickers.
 */
export function useAiModelOptions(): string[] {
  const fetchContext = useFetchContext();
  const authHeaders = useAuthHeaders();
  const { data: modelProviders = [] } = useFetch<IntegrationI[]>(
    `/integrations/provider?category=${IntegrationCategory.AI_MODEL}&activeOnly=true`,
    { staleTime: STALE_TIME_DROPDOWN },
  );
  const perProviderQueries = useQueries(
    modelProviders.map((provider) => ({
      queryKey: [
        fetchContext.stack,
        `/integrations/provider/${provider.name}/integration`,
      ],
      queryFn: (): Promise<ModelDto[]> =>
        fetchWithContext(
          `/integrations/provider/${provider.name}/integration?activeOnly=true`,
          fetchContext,
          { headers: authHeaders },
        ),
      staleTime: STALE_TIME_DROPDOWN,
      enabled: fetchContext.ready,
    })),
  );

  return modelProviders
    .flatMap((provider, i) => {
      const models =
        (perProviderQueries[i]?.data as ModelDto[] | undefined) ?? [];
      return models.map((m) => `${provider.name}/${m.api}`);
    })
    .sort();
}
