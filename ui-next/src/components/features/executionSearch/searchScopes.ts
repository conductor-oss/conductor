export const MONO_FONT =
  'ui-monospace, SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace';

/**
 * Fields the scoped search bar on the execution search pages can search.
 * Every id-like scope is an exact match that accepts several values; free text
 * is sent as the `freeText` search param, whose words must all match.
 */
export type SearchScope =
  | "workflowId"
  | "correlationId"
  | "idempotencyKey"
  | "freeText";

export interface SearchScopeConfig {
  label: string;
  placeholder: string;
  hint: string;
}

const EXACT_MATCH_HINT =
  "Exact match · paste several separated by commas or spaces";

export const SEARCH_SCOPES: Record<SearchScope, SearchScopeConfig> = {
  workflowId: {
    label: "Workflow id",
    placeholder: "Paste one or more workflow ids",
    hint: EXACT_MATCH_HINT,
  },
  correlationId: {
    label: "Correlation id",
    placeholder: "Paste one or more correlation ids",
    hint: EXACT_MATCH_HINT,
  },
  idempotencyKey: {
    label: "Idempotency key",
    placeholder: "Paste one or more idempotency keys",
    hint: EXACT_MATCH_HINT,
  },
  freeText: {
    label: "Free text",
    placeholder:
      "Words from input, output, variables, task outputs or failure reasons",
    hint: "Words in input, output, variables, task outputs and failure reasons (not task inputs) · 3–49 characters · all words must match",
  },
};

/**
 * Scope config with the execution id worded for the page, e.g. "Workflow id" on
 * workflow executions and "Execution id" on agent executions.
 */
export const searchScopesFor = (
  idLabel: string,
): Record<SearchScope, SearchScopeConfig> => ({
  ...SEARCH_SCOPES,
  workflowId: {
    ...SEARCH_SCOPES.workflowId,
    label: idLabel,
    placeholder: `Paste one or more ${idLabel.toLowerCase()}s`,
  },
});

export const SEARCH_SCOPE_ORDER: SearchScope[] = [
  "workflowId",
  "correlationId",
  "idempotencyKey",
  "freeText",
];

/**
 * Splits what the user typed into the values to filter on. Ids are split on
 * commas and whitespace so a pasted list becomes several values. Free text is
 * split on whitespace only, matching how the backend splits the `freeText`
 * param into words that must all match.
 */
export const parseSearchInput = (scope: SearchScope, raw: string): string[] => {
  const separator = scope === "freeText" ? /\s+/ : /[\s,]+/;
  return raw.trim().split(separator).filter(Boolean);
};

/** Appends values to an existing list, skipping ones already present. */
export const mergeValues = (existing: string[], added: string[]): string[] => {
  const merged = [...existing];
  added.forEach((value) => {
    if (!merged.includes(value)) {
      merged.push(value);
    }
  });
  return merged;
};

/** `workflowId` lives in the URL as one comma-separated string. */
export const splitWorkflowIds = (workflowId: string): string[] =>
  workflowId
    .split(",")
    .map((id) => id.trim())
    .filter(Boolean);

export const workflowIdClause = (workflowIds: string[]): string | null => {
  if (workflowIds.length === 0) {
    return null;
  }
  if (workflowIds.length === 1) {
    return `workflowId='${workflowIds[0]}'`;
  }
  return `workflowId IN (${workflowIds.join(",")})`;
};

/** Free text lives in the URL as one string of space-separated words. */
export const splitFreeText = (freeText: string): string[] =>
  parseSearchInput("freeText", freeText);
