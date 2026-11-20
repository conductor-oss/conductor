import { useCallback, useState } from "react";
import { useQueryState } from "react-router-use-location-state";
import { commonlyUsedDateTime } from "utils/date";
import { basicOnlyFilterQuery } from "pages/executions/workflowSearchComponents/basicFilterQuery";

/**
 * Owns every url-backed filter on the agent execution search, so the key names
 * and their defaults are declared once rather than repeated in AgentSearch,
 * BasicSearch and AdvancedSearch.
 *
 * The agent search filters on the same fields as the workflow one minus
 * "exclude sub-executions", so the clause building and parsing in
 * basicFilterQuery.ts is shared rather than duplicated here.
 *
 * Pagination and table state (page, rowsPerPage, sort, displayCode) are
 * deliberately left out: each search mode manages its own table.
 */
export const useAgentSearchFilters = () => {
  const [asQuery, setAsQuery] = useQueryState("asQuery", false);
  const [freeText, setFreeText] = useQueryState("freeText", "");
  const [status, setStatus] = useQueryState<string[]>("status", []);

  const [workflowType, setWorkflowType] = useQueryState<string[]>(
    "workflowType",
    [],
  );
  const [workflowId, setWorkflowId] = useQueryState("workflowId", "");
  const [correlationIds, setCorrelationIds] = useQueryState<string[]>(
    "correlationIds",
    [],
  );
  const [idempotencyKey, setIdempotencyKey] = useQueryState<string[]>(
    "idempotencyKey",
    [],
  );
  const [modifiedFrom, setModifiedFrom] = useQueryState("modifiedFrom", "");
  const [modifiedTo, setModifiedTo] = useQueryState("modifiedTo", "");

  const [startTimeFrom, setStartTimeFrom] = useQueryState(
    "startFrom",
    commonlyUsedDateTime("last72Hours").rangeStart,
  );
  const [startTimeTo, setStartTimeTo] = useQueryState("startTo", "");
  const [endTimeFrom, setEndTimeFrom] = useQueryState("endTimeFrom", "");
  const [endTimeTo, setEndTimeTo] = useQueryState("endTimeTo", "");

  /**
   * The clauses for the filters that only basic search renders a control for.
   * Frozen at mount: it seeds the query box when SQL format is switched on,
   * and re-deriving it afterwards would fight whatever the user then typed.
   */
  const [seededQuery] = useState(() =>
    basicOnlyFilterQuery({
      workflowType,
      workflowId,
      correlationIds,
      idempotencyKey,
      modifiedFrom,
      modifiedTo,
    }),
  );

  // The same `query` key is read twice with different defaults, on purpose.
  // useQueryState does not write a value equal to its default, so:
  //   - authoredQuery is empty until the user actually edits the box, which is
  //     how switching the toggle off knows to leave the basic fields alone;
  //   - effectiveQuery falls back to the seed, so the box shows the equivalent
  //     query and the first search already carries those clauses.
  const [authoredQuery, setEmptyDefaultQuery] = useQueryState("query", "");
  const [effectiveQuery, setQuery] = useQueryState("query", seededQuery);

  /**
   * Removes the param entirely rather than writing an empty value, so the box
   * is seeded afresh the next time SQL format is switched on. Editing the box
   * goes through setQuery instead, where an empty value differs from the
   * seeded default and so persists as "explicitly cleared".
   */
  const clearQuery = useCallback(
    () => setEmptyDefaultQuery(""),
    [setEmptyDefaultQuery],
  );

  /**
   * Clears every filter, whichever mode Reset was pressed in. Each mode used
   * to clear only the fields it renders, so resetting in SQL format left the
   * basic-only filters set and they reappeared on switching back.
   *
   * The query is written as an explicit empty rather than removed: seededQuery
   * is frozen at mount, so dropping the param would fall back to the seed as
   * it was before the reset.
   */
  const resetFilters = useCallback(() => {
    setStatus([]);
    setFreeText("");
    setWorkflowType([]);
    setWorkflowId("");
    setCorrelationIds([]);
    setIdempotencyKey([]);
    setModifiedFrom("");
    setModifiedTo("");
    setStartTimeFrom("");
    setStartTimeTo("");
    setEndTimeFrom("");
    setEndTimeTo("");
    setQuery("");
  }, [
    setStatus,
    setFreeText,
    setWorkflowType,
    setWorkflowId,
    setCorrelationIds,
    setIdempotencyKey,
    setModifiedFrom,
    setModifiedTo,
    setStartTimeFrom,
    setStartTimeTo,
    setEndTimeFrom,
    setEndTimeTo,
    setQuery,
  ]);

  return {
    asQuery,
    setAsQuery,
    freeText,
    setFreeText,
    status,
    setStatus,
    workflowType,
    setWorkflowType,
    workflowId,
    setWorkflowId,
    correlationIds,
    setCorrelationIds,
    idempotencyKey,
    setIdempotencyKey,
    modifiedFrom,
    setModifiedFrom,
    modifiedTo,
    setModifiedTo,
    startTimeFrom,
    setStartTimeFrom,
    startTimeTo,
    setStartTimeTo,
    endTimeFrom,
    setEndTimeFrom,
    endTimeTo,
    setEndTimeTo,
    authoredQuery,
    effectiveQuery,
    setQuery,
    clearQuery,
    resetFilters,
    seededQuery,
  } as const;
};
