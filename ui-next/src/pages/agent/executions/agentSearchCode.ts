export type BuildQueryOutput = {
  query: string;
  freeText: string;
  start: number;
  size: number;
  sort: string;
  classifier: string;
  topLevelOnly: boolean;
};

/**
 * Builds a search URL that preserves the filters applied on the Agent Executions page.
 */
export const buildEndpoint = ({
  start,
  size,
  sort,
  freeText,
  query,
  classifier,
  topLevelOnly,
}: BuildQueryOutput) =>
  `${window.location.origin}/api/workflow/search?${new URLSearchParams({
    start: String(start),
    size: String(size),
    sort,
    freeText,
    query,
    classifier,
    topLevelOnly: String(topLevelOnly),
  }).toString()}`;
