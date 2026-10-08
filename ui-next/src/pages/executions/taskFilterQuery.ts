import _isEmpty from "lodash/isEmpty";
import { parseClause, splitClauses } from "./queryClauses";

/**
 * The task execution filters that only basic search renders a control for.
 * Advanced (SQL format) search has no field for any of them, so unless they are
 * translated into query text they disappear from both the SQL box and the
 * request when the toggle is flipped.
 *
 * Free text and the start/end time ranges are deliberately absent: advanced
 * search renders its own controls for those, and TaskSearch's buildQuery
 * appends the time clauses in both modes, so duplicating them into the query
 * text would produce the clause twice.
 */
export type BasicOnlyTaskFilters = {
  taskDefName?: string;
  taskType?: string[];
  taskId?: string;
  taskRefName?: string;
  workflowName?: string;
  status?: string[];
};

/**
 * Clause strings for the basic-only filters, in the same formats TaskSearch's
 * buildQuery emits — keep the two in sync.
 */
export const basicOnlyTaskFilterClauses = ({
  taskDefName,
  taskType,
  taskId,
  taskRefName,
  workflowName,
  status,
}: BasicOnlyTaskFilters): string[] => {
  const clauses: string[] = [];

  if (!_isEmpty(taskDefName)) {
    clauses.push(`taskDefName='${taskDefName}'`);
  }
  if (!_isEmpty(taskType)) {
    clauses.push(`taskType IN (${taskType!.join(",")})`);
  }
  if (!_isEmpty(taskId)) {
    clauses.push(`taskId='${taskId}'`);
  }
  if (!_isEmpty(taskRefName)) {
    clauses.push(`referenceTaskName='${taskRefName}'`);
  }
  if (!_isEmpty(workflowName)) {
    clauses.push(`workflowName='${workflowName}'`);
  }
  if (!_isEmpty(status)) {
    clauses.push(`status IN (${status!.join(",")})`);
  }

  return clauses;
};

/** The basic-only filters as a single query-text fragment. */
export const basicOnlyTaskFilterQuery = (
  filters: BasicOnlyTaskFilters,
): string => basicOnlyTaskFilterClauses(filters).join(" AND ");

/** Every filter task basic search can express, as parsed out of query text. */
export type ParsedBasicTaskFilters = BasicOnlyTaskFilters & {
  startTimeFrom?: string;
  startTimeTo?: string;
  endTimeFrom?: string;
  endTimeTo?: string;
};

/**
 * Reads query text back into basic search's fields — the inverse of
 * basicOnlyTaskFilterQuery, extended to the time bounds advanced search also
 * has controls for so their text values win over the controls when switching
 * back.
 *
 * Clause parsing itself lives in queryClauses.ts; this only maps a recognised
 * field and operator onto the form.
 *
 * Returns null when the query uses anything basic search cannot express (OR,
 * grouping, an unknown field, an operator a field does not support, ambiguous
 * quoting), which is the caller's signal to ask before discarding it. Parsing
 * is all-or-nothing: applying only the clauses we understood would silently
 * drop the rest.
 */
export const parseQueryToBasicTaskFilters = (
  queryText: string,
): ParsedBasicTaskFilters | null => {
  const clauses = splitClauses(queryText);
  if (clauses.length === 0) {
    return {};
  }
  // Basic search joins every field with AND and has no way to group clauses.
  if (/\bOR\b/i.test(queryText)) {
    return null;
  }

  const filters: ParsedBasicTaskFilters = {};

  for (const rawClause of clauses) {
    const parsed = parseClause(rawClause);
    if (!parsed) {
      return null;
    }
    const { field, operator, values } = parsed;
    const single = values[0];

    switch (`${field}:${operator}`) {
      case "taskDefName:=":
        filters.taskDefName = single;
        break;
      case "taskType:IN":
      case "taskType:=":
        filters.taskType = values;
        break;
      case "taskId:=":
        filters.taskId = single;
        break;
      case "referenceTaskName:=":
        filters.taskRefName = single;
        break;
      case "workflowName:=":
        filters.workflowName = single;
        break;
      case "status:IN":
      case "status:=":
        filters.status = values;
        break;
      case "startTime:>":
        filters.startTimeFrom = single;
        break;
      case "startTime:<":
        filters.startTimeTo = single;
        break;
      case "endTime:>":
        filters.endTimeFrom = single;
        break;
      case "endTime:<":
        filters.endTimeTo = single;
        break;
      default:
        return null;
    }
  }

  return filters;
};

/** Every field task basic search owns, with a concrete value for each. */
export type BasicTaskFieldValues = {
  taskDefName: string;
  taskType: string[];
  taskId: string;
  taskRefName: string;
  workflowName: string;
  status: string[];
  startTimeFrom: string;
  startTimeTo: string;
  endTimeFrom: string;
  endTimeTo: string;
};

/** The fields advanced search renders its own control for. */
export type SharedTaskFieldValues = Pick<
  BasicTaskFieldValues,
  "startTimeFrom" | "startTimeTo" | "endTimeFrom" | "endTimeTo"
>;

/**
 * The values to write into basic search's fields when SQL format is switched
 * off, given the parsed query and what the shared controls currently hold.
 *
 * Basic-only fields are replaced from the query, because while SQL format is on
 * the query text is their only source — a clause the user deleted should clear
 * the field.
 *
 * The time bounds are different: advanced search renders controls for them, and
 * the query only overrides a control when it mentions that field. A query that
 * says nothing about startTime must therefore leave the picker's value alone,
 * or switching back would wipe a filter that was applied.
 */
export const basicTaskFieldsAfterQueryFormat = (
  parsed: ParsedBasicTaskFilters,
  current: SharedTaskFieldValues,
): BasicTaskFieldValues => ({
  taskDefName: parsed.taskDefName ?? "",
  taskType: parsed.taskType ?? [],
  taskId: parsed.taskId ?? "",
  taskRefName: parsed.taskRefName ?? "",
  workflowName: parsed.workflowName ?? "",
  status: parsed.status ?? [],
  startTimeFrom: parsed.startTimeFrom ?? current.startTimeFrom,
  startTimeTo: parsed.startTimeTo ?? current.startTimeTo,
  endTimeFrom: parsed.endTimeFrom ?? current.endTimeFrom,
  endTimeTo: parsed.endTimeTo ?? current.endTimeTo,
});
