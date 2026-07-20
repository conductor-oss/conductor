/**
 * Routing for the agent executions combined search input.
 * Keeps structured query vs freeText decisions out of the React component so
 * they can be unit-tested without mounting the full search form.
 */

/** Conductor execution IDs are typically UUIDs. */
export const WORKFLOW_ID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Exact workflowId equality. Only full UUIDs are supported — Orkes/ES does not
 * treat `*` inside a quoted workflowId value as a wildcard.
 */
export function workflowIdClause(value: string): string {
  return `workflowId=${quoteQueryValue(value.trim().toLowerCase())}`;
}

/**
 * Postgres freeText uses to_tsquery(), which treats many characters as
 * operators (e.g. !, &, |, -, /). Those terms must use structured query
 * filters instead (workflowType / workflowId).
 */
export function isUnsafeForFreeText(value: string): boolean {
  return /[^a-zA-Z0-9_\s]/.test(value);
}

/** Quote a value for Conductor's query DSL; the server strips the quotes. */
export function quoteQueryValue(value: string): string {
  return `'${value.replace(/'/g, "")}'`;
}

function wildcardToRegExp(pattern: string): RegExp {
  const escaped = pattern.replace(/[.+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`^${escaped.replace(/\*/g, ".*")}$`, "i");
}

/** Match a search term against known agent names (substring, or * wildcards). */
export function matchAgentNames(term: string, agentNames: string[]): string[] {
  const trimmed = term.trim();
  if (!trimmed) {
    return [];
  }
  if (trimmed.includes("*")) {
    const re = wildcardToRegExp(trimmed);
    return agentNames.filter((name) => re.test(name));
  }
  const lower = trimmed.toLowerCase();
  // Substring so "aa" matches agent "aaaa" (exact match alone is too strict).
  return agentNames.filter((name) => name.toLowerCase().includes(lower));
}

/** Build a workflowType clause, quoting values that need it. */
export function workflowTypeClause(names: string[]): string {
  if (names.length === 1 && names[0].includes("*")) {
    return `workflowType=${names[0]}`;
  }
  return `workflowType IN (${names.map(quoteQueryValue).join(",")})`;
}

export type CombinedSearchResolution = {
  /** Query clauses contributed by the search input alone. */
  searchClauses: string[];
  freeText: string;
  /** True when the input was routed to a workflowId (execution id) filter. */
  isExecutionIdSearch: boolean;
};

/**
 * Resolve the combined search box into structured query clauses and/or freeText.
 * Does not include status, date, or dropdown agent-name filters — callers AND
 * those in separately.
 *
 * Execution IDs require a full UUID. Partial IDs are not supported.
 */
export function resolveCombinedSearch(
  searchInput: string,
  {
    selectedTypes = [],
    knownAgentNames = [],
  }: {
    selectedTypes?: string[];
    knownAgentNames?: string[];
  } = {},
): CombinedSearchResolution {
  const search = searchInput.trim();
  const searchClauses: string[] = [];
  let freeText = "*";
  let isExecutionIdSearch = false;

  if (!search) {
    return { searchClauses, freeText, isExecutionIdSearch };
  }

  if (WORKFLOW_ID_RE.test(search)) {
    searchClauses.push(workflowIdClause(search));
    isExecutionIdSearch = true;
  } else if (selectedTypes.length === 0) {
    const matchedAgents = matchAgentNames(search, knownAgentNames);
    // Prefer workflowType when: known agent, wildcard, or freeText-unsafe
    // (e.g. @!@&/2/). Agent executions often use names that are not in
    // /agent/list, so unsafe terms still try exact workflowType match.
    if (matchedAgents.length) {
      searchClauses.push(workflowTypeClause(matchedAgents));
    } else if (search.includes("*") || isUnsafeForFreeText(search)) {
      searchClauses.push(workflowTypeClause([search]));
    } else {
      freeText = search;
    }
  } else if (!isUnsafeForFreeText(search)) {
    freeText = search;
  }
  // else: agent filter already applied; skip freeText — special characters
  // would break to_tsquery and are not safe to AND as another field.

  return { searchClauses, freeText, isExecutionIdSearch };
}
