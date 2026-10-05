export const MONO_FONT =
  'ui-monospace, SFMono-Regular, Menlo, Consolas, "Liberation Mono", monospace';

/** Key of a field the scoped search bar can search, e.g. "correlationId". */
export type SearchScope = string;

/**
 * A field the scoped search bar can search. Exact-match fields accept several
 * values; word-match fields (free text) take words that must all match.
 */
export interface SearchScopeConfig {
  key: SearchScope;
  label: string;
  placeholder: string;
  hint: string;
  /** Free text: split on whitespace only and shown as "contains". */
  matchesWords?: boolean;
  /** Ids and keys read better in a fixed-width font in chips. */
  monospace?: boolean;
}

export const EXACT_MATCH_HINT =
  "Exact match · paste several separated by commas or spaces";

/** An exact-match field: "Correlation id" → "Paste one or more correlation ids". */
export const exactScope = (
  key: SearchScope,
  label: string,
  monospace = true,
): SearchScopeConfig => ({
  key,
  label,
  placeholder: `Paste one or more ${label.toLowerCase()}s`,
  hint: EXACT_MATCH_HINT,
  monospace,
});

export const freeTextScope = (
  placeholder: string,
  hint: string,
): SearchScopeConfig => ({
  key: "freeText",
  label: "Free text",
  placeholder,
  hint,
  matchesWords: true,
});

/**
 * The workflow execution search fields, with the execution id worded for the
 * page, e.g. "Workflow id" on workflow executions and "Execution id" on agent
 * executions.
 */
export const searchScopesFor = (idLabel: string): SearchScopeConfig[] => [
  exactScope("workflowId", idLabel),
  exactScope("correlationId", "Correlation id"),
  exactScope("idempotencyKey", "Idempotency key"),
  freeTextScope(
    "Words from input, output, variables, task outputs or failure reasons",
    "Words in input, output, variables, task outputs and failure reasons (not task inputs) · 3–49 characters · all words must match",
  ),
];

/**
 * Splits what the user typed into the values to filter on. Exact-match values
 * are split on commas and whitespace so a pasted list becomes several values.
 * Words are split on whitespace only, matching how the backend splits the
 * `freeText` param into words that must all match.
 */
export const parseSearchInput = (
  scope: Pick<SearchScopeConfig, "matchesWords">,
  raw: string,
): string[] => {
  const separator = scope.matchesWords ? /\s+/ : /[\s,]+/;
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

/** Reads a field kept in the URL as one comma-separated string. */
export const splitList = (value: string): string[] =>
  value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);

/** `field='a'` for one value, `field IN (a,b)` for several, null for none. */
export const exactClause = (field: string, values: string[]): string | null => {
  if (values.length === 0) {
    return null;
  }
  if (values.length === 1) {
    return `${field}='${values[0]}'`;
  }
  return `${field} IN (${values.join(",")})`;
};

/** Free text lives in the URL as one string of space-separated words. */
export const splitFreeText = (freeText: string): string[] =>
  parseSearchInput({ matchesWords: true }, freeText);
