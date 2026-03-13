import { useMachine } from "@xstate/react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { pluginRegistry } from "plugins/registry";
import { useAuthHeaders } from "utils/query";
import {
  HookActions,
  HookState,
  SearchActionTypes,
  SearchResultItem,
} from "./types";
import { searchResultExtractor } from "./helpers";
import { searchMachine } from "./machine";

export const useSearchMachine = (): [HookState, HookActions] => {
  const authHeaders = useAuthHeaders();
  const [state, send] = useMachine(searchMachine, {
    ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
    context: {
      authHeaders,
    },
  });

  const searchTerm = state.context.searchTerm;
  const maxSearchResults = state.context.maxSearchResults;

  const setSearchTerm = useCallback(
    (searchTerm: string, count?: number) =>
      send({
        type: SearchActionTypes.UPDATE_SEARCH_TERM,
        searchTerm,
        count,
      }),
    [send],
  );

  // Core OSS data from machine context
  const taskDefinitions = state.context.taskDefinitions;
  const workflowDefinitions = state.context.workflowDefinitions;
  const scheduler = state.context.schedulers;
  const events = state.context.events;

  // Fetch data from plugin-registered search providers
  const [pluginData, setPluginData] = useState<Record<string, any[]>>({});

  useEffect(() => {
    const providers = pluginRegistry.getSearchProviders();
    if (providers.length === 0) return;

    let cancelled = false;

    Promise.all(
      providers.map(async (provider) => {
        try {
          const data = await provider.fetcher(authHeaders);
          return { id: provider.id, data };
        } catch {
          return { id: provider.id, data: [] };
        }
      }),
    ).then((results) => {
      if (cancelled) return;
      const dataMap: Record<string, any[]> = {};
      for (const { id, data } of results) {
        dataMap[id] = data;
      }
      setPluginData(dataMap);
    });

    return () => {
      cancelled = true;
    };
    // authHeaders identity is stable across renders so this is safe
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Compute search results combining core + plugin data
  const searchResults = useMemo(() => {
    // Core OSS search results
    const coreResults = searchResultExtractor({
      taskDefinitions,
      workflowDefinitions,
      scheduler,
      events,
      searchTerm,
      maxSearchResults,
    });

    // Plugin search results
    const providers = pluginRegistry.getSearchProviders();
    const pluginResults: SearchResultItem[] = [];

    for (const provider of providers) {
      const data = pluginData[provider.id] ?? [];
      if (data.length > 0 || searchTerm !== "") {
        const mapped = provider.mapper(data, searchTerm) as SearchResultItem[];
        pluginResults.push(...mapped);
      }
    }

    // If no search term, return null (same as before)
    if (searchTerm === "") {
      return null;
    }

    // Merge and sort by number of sub-results
    const combined = [...(coreResults ?? []), ...pluginResults];
    return combined.sort(
      ({ sub: subA }, { sub: subB }) =>
        (subB?.length ?? 0) - (subA?.length ?? 0),
    ) as typeof coreResults;
  }, [
    taskDefinitions,
    workflowDefinitions,
    scheduler,
    events,
    searchTerm,
    maxSearchResults,
    pluginData,
  ]);

  return [
    {
      searchTerm,
      searchResults,
    },
    { setSearchTerm },
  ];
};
