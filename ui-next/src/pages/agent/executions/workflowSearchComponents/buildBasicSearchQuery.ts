/**
 * Pure query builder for agent BasicSearch.
 * Keeps filter AND-ing out of the React component so it can be unit-tested.
 */

import _isEmpty from "lodash/isEmpty";
import { dateToEpoch } from "utils";
import {
  resolveCombinedSearch,
  workflowTypeClause,
} from "./combinedSearchQuery";

export type BasicSearchQueryInput = {
  searchInput: string;
  selectedTypes: string[];
  knownAgentNames: string[];
  status: string[];
  startTimeFrom: string;
  startTimeTo: string;
  endTimeFrom: string;
  endTimeTo: string;
  modifiedFrom: string;
  modifiedTo: string;
};

export type SearchQueryFT = { query: string; freeText: string };

/** True when the structured query is a direct workflowId= lookup. */
export function isWorkflowIdQuery(query: string): boolean {
  return /(?:^|AND\s)workflowId\s*=/i.test(query);
}

/** Dedupe and case-insensitive sort for agent-name dropdown options. */
export function mergeUniqueSortedNames(...lists: string[][]): string[] {
  return Array.from(new Set(lists.flat().filter(Boolean))).sort((a, b) =>
    a.toLowerCase().localeCompare(b.toLowerCase()),
  );
}

/**
 * Build the Conductor search `query` + `freeText` for Basic Search.
 * Full UUID → identity-only workflowId clause (no date/status/agent AND).
 */
export function buildBasicSearchQuery(
  input: BasicSearchQueryInput,
): SearchQueryFT {
  const {
    searchInput,
    selectedTypes,
    knownAgentNames,
    status,
    startTimeFrom,
    startTimeTo,
    endTimeFrom,
    endTimeTo,
    modifiedFrom,
    modifiedTo,
  } = input;

  const clauses: string[] = [];

  const {
    searchClauses,
    freeText: resolvedFreeText,
    isExecutionIdSearch,
  } = resolveCombinedSearch(searchInput, {
    selectedTypes,
    knownAgentNames,
  });

  // Full execution-id lookup is identity-only: do not AND other filters
  // (date window, agent name, status) or a matching id can still return 0.
  // Partial IDs are not supported — paste the full UUID.
  if (isExecutionIdSearch) {
    return {
      query: searchClauses.join(" AND "),
      freeText: "*",
    };
  }

  if (!_isEmpty(selectedTypes)) {
    clauses.push(workflowTypeClause(selectedTypes));
  }
  if (!_isEmpty(status)) {
    clauses.push(`status IN (${status.join(",")})`);
  }
  if (!_isEmpty(startTimeFrom)) {
    clauses.push(`startTime>${dateToEpoch(startTimeFrom)}`);
  }
  if (!_isEmpty(startTimeTo)) {
    clauses.push(`startTime<${dateToEpoch(startTimeTo)}`);
  }
  if (!_isEmpty(endTimeFrom)) {
    clauses.push(`endTime>${dateToEpoch(endTimeFrom)}`);
  }
  if (!_isEmpty(endTimeTo)) {
    clauses.push(`endTime<${dateToEpoch(endTimeTo)}`);
  }
  if (!_isEmpty(modifiedFrom)) {
    clauses.push(`modifiedTime>${modifiedFrom}`);
  }
  if (!_isEmpty(modifiedTo)) {
    clauses.push(`modifiedTime<${modifiedTo}`);
  }

  clauses.push(...searchClauses);

  return {
    query: clauses.join(" AND "),
    freeText: resolvedFreeText,
  };
}
