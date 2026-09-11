/**
 * Parsing for the search query language used by the execution search box.
 *
 * There is no single authoritative grammar for this language, and the two
 * backends do not agree on one:
 *
 * - Postgres (`PostgresIndexQueryBuilder`) splits on the literal `" AND "`,
 *   matches `([a-zA-Z]+)\s?(=|>|<|IN)\s?(.*)`, then strips every `"`, `'`, `(`
 *   and `)` from the value before splitting it on commas. It has no `OR`.
 * - Elasticsearch (`es7 dao.query.parser`) tokenises the query and supports
 *   `OR` and `!=` as well.
 *
 * So byte-compatibility with "the backend" is not a reachable target. This
 * module takes a deliberate line instead:
 *
 * - Clause splitting follows the strictest reading, because getting clause
 *   boundaries wrong changes what a query means. `AND` is case-sensitive, as
 *   in Postgres and in cliSearch.ts.
 * - Recognising an operator is lenient, because that only ever widens what we
 *   understand and never changes a clause's meaning. `IN` is case-insensitive,
 *   which matters because the example query the UI itself shows uses `in`.
 * - Anything ambiguous is refused rather than guessed. Callers treat null as
 *   "cannot be represented", so a refusal is always safe; a wrong parse is not.
 *
 * TODO: cliSearch.ts still defines its own clause regexes. Migrate it onto
 * this module so the codebase has one parser rather than several.
 */

export type ClauseOperator = "=" | ">" | "<" | "IN";

export type ParsedClause = {
  field: string;
  operator: ClauseOperator;
  /** One entry for `=`/`>`/`<`; one per list item for `IN`. */
  values: string[];
};

const IN_CLAUSE = /^(\w+)\s+IN\s*\(([^()]*)\)$/i;
const OPERATOR_CLAUSE = /^(\w+)\s*(=|>|<)\s*(.*)$/;

/**
 * Splits a query into its clauses. Case-sensitive on purpose: a lower case
 * "and" is part of a value to Postgres, not a separator, and treating it as a
 * separator here would silently split one clause into two.
 */
export const splitClauses = (query: string): string[] =>
  query
    .trim()
    // The edge alternatives matter: without them a dangling separator, as in
    // the half-typed `startTime>100 AND `, is absorbed into the value and
    // silently filters on "100 AND".
    .split(/\s+AND\s+|\s+AND$|^AND\s+/)
    .map((clause) => clause.trim())
    .filter((clause) => clause !== "");

/** Whether a value carries a quote on one end only, so its extent is unclear. */
const hasUnbalancedQuote = (value: string) => {
  const trimmed = value.trim();
  const opensWithQuote = /^['"]/.test(trimmed);
  const closesWithQuote = /['"]$/.test(trimmed);
  if (trimmed.length === 1 && opensWithQuote) {
    return true;
  }
  return opensWithQuote !== closesWithQuote;
};

const unquote = (value: string) =>
  value.trim().replace(/^(['"])([\s\S]*)\1$/, "$2");

/**
 * Reads a single clause. Returns null when the clause is not a recognisable
 * `field op value`, or when quoting leaves the values ambiguous — notably
 * `IN ('a,b')`, where the comma could be a separator or part of the value.
 * Postgres resolves that by stripping the quotes and splitting anyway; rather
 * than pick a side and be silently wrong for the other, this refuses.
 */
export const parseClause = (clause: string): ParsedClause | null => {
  const trimmed = clause.trim();

  const inMatch = trimmed.match(IN_CLAUSE);
  if (inMatch) {
    const [, field, rawValues] = inMatch;
    const items = rawValues.split(",");
    if (items.some(hasUnbalancedQuote)) {
      return null;
    }
    const values = items.map(unquote).filter((value) => value !== "");
    // An empty list would otherwise read as "no filter on this field".
    if (values.length === 0) {
      return null;
    }
    return { field, operator: "IN", values };
  }

  const opMatch = trimmed.match(OPERATOR_CLAUSE);
  if (opMatch) {
    const [, field, operator, rawValue] = opMatch;
    if (hasUnbalancedQuote(rawValue)) {
      return null;
    }
    // An empty value is meaningful for `=` — `parentWorkflowId=""` is how
    // "has no parent" is expressed — so it is kept rather than filtered out.
    return {
      field,
      operator: operator as ClauseOperator,
      values: [unquote(rawValue)],
    };
  }

  return null;
};
