/**
 * Stress tests for the clause parser.
 *
 * The shapes below come from three places: what the UI generates
 * (BasicSearch.buildQuery), what the UI tells users to write (the example in
 * SearchExampleQuery.tsx, which uses lower case `in`, quoted list values and
 * spaced commas), and what the backends accept. Where Postgres and
 * Elasticsearch disagree, the expectation records the line this module takes
 * rather than pretending there is one right answer.
 */
import { describe, expect, it } from "vitest";
import { parseClause, splitClauses } from "./queryClauses";

describe("splitClauses", () => {
  it("splits on AND", () => {
    expect(splitClauses("a=1 AND b=2")).toEqual(["a=1", "b=2"]);
  });

  it("tolerates extra whitespace around the separator", () => {
    expect(splitClauses("a=1   AND\tb=2")).toEqual(["a=1", "b=2"]);
  });

  it("does not split on a lower case and", () => {
    // Postgres splits on the literal " AND ", so this is one clause to it.
    expect(splitClauses("a=1 and b=2")).toEqual(["a=1 and b=2"]);
  });

  it("does not split on AND inside a word", () => {
    expect(splitClauses("brandName='x'")).toEqual(["brandName='x'"]);
  });

  it("drops a dangling separator instead of folding it into a value", () => {
    // Half-typed queries are common; absorbing the AND would silently filter
    // on "1 AND".
    expect(splitClauses("a=1 AND ")).toEqual(["a=1"]);
    expect(splitClauses("AND a=1")).toEqual(["a=1"]);
  });

  it("leaves a doubled separator to be refused downstream", () => {
    // "AND b=2" is not a parseable clause, so the query is refused rather than
    // half-applied.
    expect(splitClauses("a=1 AND  AND b=2")).toEqual(["a=1", "AND b=2"]);
    expect(parseClause("AND b=2")).toBeNull();
  });

  it("returns nothing for an empty query", () => {
    expect(splitClauses("")).toEqual([]);
    expect(splitClauses("   ")).toEqual([]);
  });
});

describe("parseClause — shapes the UI generates", () => {
  const cases: [string, ReturnType<typeof parseClause>][] = [
    [
      "workflowType IN (order_flow)",
      { field: "workflowType", operator: "IN", values: ["order_flow"] },
    ],
    [
      "workflowType IN (a,b)",
      { field: "workflowType", operator: "IN", values: ["a", "b"] },
    ],
    [
      "workflowId='abc-123'",
      { field: "workflowId", operator: "=", values: ["abc-123"] },
    ],
    [
      "modifiedTime>1000",
      { field: "modifiedTime", operator: ">", values: ["1000"] },
    ],
    [
      "modifiedTime<2000",
      { field: "modifiedTime", operator: "<", values: ["2000"] },
    ],
    [
      'parentWorkflowId=""',
      { field: "parentWorkflowId", operator: "=", values: [""] },
    ],
  ];

  it.each(cases)("parses %s", (clause, expected) => {
    expect(parseClause(clause)).toEqual(expected);
  });
});

describe("parseClause — shapes the UI tells users to write", () => {
  it("accepts a lower case in", () => {
    expect(parseClause("status in ('RUNNING')")).toEqual({
      field: "status",
      operator: "IN",
      values: ["RUNNING"],
    });
  });

  it("accepts quoted list values with spaces around the commas", () => {
    expect(parseClause("status in ('RUNNING' , 'COMPLETED')")).toEqual({
      field: "status",
      operator: "IN",
      values: ["RUNNING", "COMPLETED"],
    });
  });

  it("accepts double quotes as well as single", () => {
    expect(parseClause('workflowId="abc"')).toEqual({
      field: "workflowId",
      operator: "=",
      values: ["abc"],
    });
  });

  it("accepts space around the operator", () => {
    expect(parseClause("startTime > 100")).toEqual({
      field: "startTime",
      operator: ">",
      values: ["100"],
    });
  });

  it("accepts no space around IN's parenthesis", () => {
    expect(parseClause("status IN('FAILED')")).toEqual({
      field: "status",
      operator: "IN",
      values: ["FAILED"],
    });
  });
});

describe("parseClause — values that need care", () => {
  it("keeps a value containing a colon", () => {
    expect(parseClause("workflowId='ns:abc'")?.values).toEqual(["ns:abc"]);
  });

  it("keeps a value containing spaces", () => {
    expect(parseClause("workflowType='my flow'")?.values).toEqual(["my flow"]);
  });

  it("keeps an unquoted value containing an @", () => {
    expect(parseClause("createdBy=mail@example.com")?.values).toEqual([
      "mail@example.com",
    ]);
  });

  it("keeps a value containing the word AND", () => {
    // splitClauses would have separated a real AND before this point.
    expect(parseClause("workflowType='a and b'")?.values).toEqual(["a and b"]);
  });

  it("drops empty entries in a list", () => {
    expect(parseClause("status IN (A,,B)")?.values).toEqual(["A", "B"]);
  });
});

describe("parseClause — refused", () => {
  const refused = [
    ["an IN list whose quoting is ambiguous", "correlationId IN ('a,b')"],
    ["a value with a quote on one end only", "workflowId='abc"],
    ["a list item with a quote on one end only", "status IN ('A,B','C')"],
    ["an empty IN list", "status IN ()"],
    ["a list of only empty entries", "status IN (,)"],
    ["nested parentheses in a list", "workflowType IN (a(b))"],
    ["a field with no operator", "workflowType"],
    ["an operator with no field", "='abc'"],
    ["a dotted input field", "input . Age = 10"],
    ["an unsupported operator", "status!='FAILED'"],
    ["a bare word", "RUNNING"],
    ["an empty clause", ""],
  ];

  it.each(refused)("refuses %s", (__label, clause) => {
    expect(parseClause(clause)).toBeNull();
  });
});

describe("parseClause — where we knowingly differ from Postgres", () => {
  it("refuses ambiguous quoting that Postgres resolves by stripping quotes", () => {
    // Postgres would strip the quotes and split, yielding ["a","b"]. Elastic-
    // search treats it as one list literal. Refusing avoids picking a side and
    // being silently wrong for the other; the caller then asks the user.
    expect(parseClause("correlationId IN ('a,b')")).toBeNull();
  });

  it("accepts more whitespace than Postgres's single optional space", () => {
    // Postgres's `\\s?` would not match two spaces. Being lenient only widens
    // what we understand; it cannot change a clause's meaning.
    expect(parseClause("startTime  >  100")?.values).toEqual(["100"]);
  });

  it("accepts field names with digits and underscores", () => {
    // Postgres matches `[a-zA-Z]+` only. Same reasoning as above.
    expect(parseClause("field_2='x'")?.field).toBe("field_2");
  });
});
