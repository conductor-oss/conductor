import _isEmpty from "lodash/isEmpty";

/**
 * The execution filters that only basic search renders a control for. Advanced
 * (SQL format) search has no field for any of them, and they are not passed to
 * it, so unless they are translated into query text they disappear from both
 * the SQL box and the request when the toggle is flipped.
 *
 * Status, free text and the start/end time ranges are deliberately absent:
 * advanced search renders its own controls for those, and duplicating them into
 * the query text would override those controls and leave them showing a value
 * that is no longer applied.
 */
export type BasicOnlyFilters = {
  workflowType?: string[];
  workflowId?: string;
  correlationIds?: string[];
  idempotencyKey?: string[];
  modifiedFrom?: string;
  modifiedTo?: string;
  excludeSubExecutions?: boolean;
};

/**
 * Clause strings for the basic-only filters, in the same formats BasicSearch's
 * buildQuery emits — keep the two in sync.
 */
export const basicOnlyFilterClauses = ({
  workflowType,
  workflowId,
  correlationIds,
  idempotencyKey,
  modifiedFrom,
  modifiedTo,
  excludeSubExecutions,
}: BasicOnlyFilters): string[] => {
  const clauses: string[] = [];

  if (!_isEmpty(workflowType)) {
    clauses.push(`workflowType IN (${workflowType!.join(",")})`);
  }
  if (!_isEmpty(workflowId)) {
    clauses.push(`workflowId='${workflowId}'`);
  }
  if (!_isEmpty(modifiedFrom)) {
    clauses.push(`modifiedTime>${modifiedFrom}`);
  }
  if (!_isEmpty(modifiedTo)) {
    clauses.push(`modifiedTime<${modifiedTo}`);
  }
  if (!_isEmpty(correlationIds)) {
    clauses.push(`correlationId IN (${correlationIds!.join(",")})`);
  }
  if (!_isEmpty(idempotencyKey)) {
    clauses.push(`idempotencyKey IN (${idempotencyKey!.join(",")})`);
  }
  if (excludeSubExecutions) {
    clauses.push(`parentWorkflowId=""`);
  }

  return clauses;
};

/** The basic-only filters as a single query-text fragment. */
export const basicOnlyFilterQuery = (filters: BasicOnlyFilters): string =>
  basicOnlyFilterClauses(filters).join(" AND ");

/** Every filter basic search can express, as parsed back out of query text. */
export type ParsedBasicFilters = {
  workflowType?: string[];
  workflowId?: string;
  correlationIds?: string[];
  idempotencyKey?: string[];
  modifiedFrom?: string;
  modifiedTo?: string;
  excludeSubExecutions?: boolean;
  status?: string[];
  startTimeFrom?: string;
  startTimeTo?: string;
  endTimeFrom?: string;
  endTimeTo?: string;
};

const unquote = (value: string) => value.trim().replace(/^(['"])(.*)\1$/, "$2");

const toList = (value: string) =>
  value
    .split(",")
    .map(unquote)
    .filter((item) => item !== "");

/**
 * Reads query text back into basic search's fields — the inverse of
 * basicOnlyFilterQuery, extended to the fields advanced search also has
 * controls for so their text values win over the controls when switching back.
 *
 * Returns null when the query uses anything basic search cannot express (OR,
 * grouping, an unknown field, an operator a field does not support), which is
 * the caller's signal to ask before discarding it. Parsing is all-or-nothing:
 * applying only the clauses we understood would silently drop the rest.
 */
export const parseQueryToBasicFilters = (
  queryText: string,
): ParsedBasicFilters | null => {
  const trimmed = queryText.trim();
  if (trimmed === "") {
    return {};
  }
  // Basic search joins every field with AND and has no way to group clauses.
  if (/\bOR\b/i.test(trimmed)) {
    return null;
  }

  const filters: ParsedBasicFilters = {};

  for (const rawClause of trimmed.split(/\s+AND\s+/i)) {
    const clause = rawClause.trim();
    if (clause === "") {
      continue;
    }

    const inMatch = clause.match(/^(\w+)\s+IN\s*\(([^()]*)\)$/i);
    const opMatch = clause.match(/^(\w+)\s*(=|>|<)\s*(.*)$/);

    const field = inMatch?.[1] ?? opMatch?.[1];
    if (!field) {
      return null;
    }
    const op = inMatch ? "IN" : opMatch![2];
    const value = inMatch ? inMatch[2] : opMatch![3];

    switch (`${field}:${op}`) {
      case "workflowType:IN":
        filters.workflowType = toList(value);
        break;
      case "workflowType:=":
        filters.workflowType = [unquote(value)];
        break;
      case "status:IN":
        filters.status = toList(value);
        break;
      case "status:=":
        filters.status = [unquote(value)];
        break;
      case "workflowId:=":
        filters.workflowId = unquote(value);
        break;
      case "correlationId:IN":
        filters.correlationIds = toList(value);
        break;
      case "correlationId:=":
        filters.correlationIds = [unquote(value)];
        break;
      case "idempotencyKey:IN":
        filters.idempotencyKey = toList(value);
        break;
      case "idempotencyKey:=":
        filters.idempotencyKey = [unquote(value)];
        break;
      case "startTime:>":
        filters.startTimeFrom = unquote(value);
        break;
      case "startTime:<":
        filters.startTimeTo = unquote(value);
        break;
      case "endTime:>":
        filters.endTimeFrom = unquote(value);
        break;
      case "endTime:<":
        filters.endTimeTo = unquote(value);
        break;
      case "modifiedTime:>":
        filters.modifiedFrom = unquote(value);
        break;
      case "modifiedTime:<":
        filters.modifiedTo = unquote(value);
        break;
      case "parentWorkflowId:=":
        // The only value basic search can express is "no parent".
        if (unquote(value) !== "") {
          return null;
        }
        filters.excludeSubExecutions = true;
        break;
      default:
        return null;
    }
  }

  return filters;
};

/** Every field basic search owns, with a concrete value for each. */
export type BasicFieldValues = {
  workflowType: string[];
  workflowId: string;
  correlationIds: string[];
  idempotencyKey: string[];
  modifiedFrom: string;
  modifiedTo: string;
  excludeSubExecutions: boolean;
  status: string[];
  startTimeFrom: string;
  startTimeTo: string;
  endTimeFrom: string;
  endTimeTo: string;
};

/** The fields advanced search renders its own control for. */
export type SharedFieldValues = Pick<
  BasicFieldValues,
  "status" | "startTimeFrom" | "startTimeTo" | "endTimeFrom" | "endTimeTo"
>;

/**
 * The values to write into basic search's fields when SQL format is switched
 * off, given the parsed query and what the shared controls currently hold.
 *
 * Basic-only fields are replaced from the query, because while SQL format is on
 * the query text is their only source — a clause the user deleted should clear
 * the field.
 *
 * Status and the time bounds are different: advanced search renders controls
 * for them, and the query only overrides a control when it mentions that field.
 * A query that says nothing about status must therefore leave the dropdown's
 * value alone, or switching back would wipe a filter that was applied.
 */
export const basicFieldsAfterQueryFormat = (
  parsed: ParsedBasicFilters,
  current: SharedFieldValues,
): BasicFieldValues => ({
  workflowType: parsed.workflowType ?? [],
  workflowId: parsed.workflowId ?? "",
  correlationIds: parsed.correlationIds ?? [],
  idempotencyKey: parsed.idempotencyKey ?? [],
  modifiedFrom: parsed.modifiedFrom ?? "",
  modifiedTo: parsed.modifiedTo ?? "",
  excludeSubExecutions: parsed.excludeSubExecutions ?? false,
  status: parsed.status ?? current.status,
  startTimeFrom: parsed.startTimeFrom ?? current.startTimeFrom,
  startTimeTo: parsed.startTimeTo ?? current.startTimeTo,
  endTimeFrom: parsed.endTimeFrom ?? current.endTimeFrom,
  endTimeTo: parsed.endTimeTo ?? current.endTimeTo,
});
