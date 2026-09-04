/**
 * Toggling "SQL format" on used to leave the query box empty, dropping every
 * filter that only basic search has a control for — from the box and from the
 * request. These pin the clause formats that carry them across.
 */
import { describe, expect, it } from "vitest";
import {
  basicFieldsAfterQueryFormat,
  basicOnlyFilterClauses,
  basicOnlyFilterQuery,
  parseQueryToBasicFilters,
} from "./basicFilterQuery";

describe("basicOnlyFilterClauses", () => {
  it("returns nothing when no basic-only filter is set", () => {
    expect(basicOnlyFilterClauses({})).toEqual([]);
    expect(
      basicOnlyFilterClauses({
        workflowType: [],
        workflowId: "",
        correlationIds: [],
        idempotencyKey: [],
        modifiedFrom: "",
        modifiedTo: "",
        excludeSubExecutions: false,
      }),
    ).toEqual([]);
  });

  it("translates a workflow name filter", () => {
    expect(
      basicOnlyFilterClauses({ workflowType: ["TestWorkflow-Aug"] }),
    ).toEqual(["workflowType IN (TestWorkflow-Aug)"]);
  });

  it("comma-separates multiple workflow names", () => {
    expect(basicOnlyFilterClauses({ workflowType: ["a", "b"] })).toEqual([
      "workflowType IN (a,b)",
    ]);
  });

  it("quotes a workflow id", () => {
    expect(basicOnlyFilterClauses({ workflowId: "abc-123" })).toEqual([
      "workflowId='abc-123'",
    ]);
  });

  it("translates correlation ids and idempotency keys", () => {
    expect(
      basicOnlyFilterClauses({
        correlationIds: ["c1", "c2"],
        idempotencyKey: ["k1"],
      }),
    ).toEqual(["correlationId IN (c1,c2)", "idempotencyKey IN (k1)"]);
  });

  it("translates the modified time range", () => {
    expect(
      basicOnlyFilterClauses({ modifiedFrom: "100", modifiedTo: "200" }),
    ).toEqual(["modifiedTime>100", "modifiedTime<200"]);
  });

  it("translates the exclude sub-executions toggle", () => {
    expect(basicOnlyFilterClauses({ excludeSubExecutions: true })).toEqual([
      'parentWorkflowId=""',
    ]);
  });

  it("leaves status, free text and start/end times to their own controls", () => {
    // Advanced search renders these itself; duplicating them into the query
    // text would disable those controls while they still showed a value.
    const clauses = basicOnlyFilterClauses({
      workflowType: ["TestWorkflow-Aug"],
    });

    expect(clauses.join(" AND ")).not.toContain("status");
    expect(clauses.join(" AND ")).not.toContain("startTime");
    expect(clauses.join(" AND ")).not.toContain("endTime");
  });
});

describe("basicOnlyFilterQuery", () => {
  it("joins the clauses with AND", () => {
    expect(
      basicOnlyFilterQuery({
        workflowType: ["TestWorkflow-Aug"],
        workflowId: "abc-123",
        excludeSubExecutions: true,
      }),
    ).toBe(
      "workflowType IN (TestWorkflow-Aug) AND workflowId='abc-123' AND parentWorkflowId=\"\"",
    );
  });

  it("is empty when there is nothing to carry over", () => {
    expect(basicOnlyFilterQuery({})).toBe("");
  });
});

describe("parseQueryToBasicFilters", () => {
  it("round-trips what basicOnlyFilterQuery produces", () => {
    const filters = {
      workflowType: ["TestWorkflow-Aug"],
      workflowId: "abc-123",
      correlationIds: ["c1", "c2"],
      idempotencyKey: ["k1"],
      modifiedFrom: "100",
      modifiedTo: "200",
      excludeSubExecutions: true,
    };

    expect(parseQueryToBasicFilters(basicOnlyFilterQuery(filters))).toEqual(
      filters,
    );
  });

  it("reads an edited workflow name back out", () => {
    expect(parseQueryToBasicFilters("workflowType IN (SomethingElse)")).toEqual(
      { workflowType: ["SomethingElse"] },
    );
  });

  it("treats an empty query as nothing to carry", () => {
    expect(parseQueryToBasicFilters("")).toEqual({});
    expect(parseQueryToBasicFilters("   ")).toEqual({});
  });

  it("reads status and the time bounds so text values win over the controls", () => {
    expect(
      parseQueryToBasicFilters(
        "status='COMPLETED' AND startTime>100 AND startTime<200 AND endTime>300 AND endTime<400",
      ),
    ).toEqual({
      status: ["COMPLETED"],
      startTimeFrom: "100",
      startTimeTo: "200",
      endTimeFrom: "300",
      endTimeTo: "400",
    });
  });

  it("accepts quoted and multi-value IN lists", () => {
    expect(
      parseQueryToBasicFilters("status IN ('FAILED','TIMED_OUT')"),
    ).toEqual({ status: ["FAILED", "TIMED_OUT"] });
  });

  it("accepts a lower case IN, as the UI's own example query uses", () => {
    expect(parseQueryToBasicFilters("workflowType in (a)")).toEqual({
      workflowType: ["a"],
    });
  });

  it("does not treat a lower case and as a separator", () => {
    // Postgres splits on the literal " AND " only, so to it this is a single
    // clause whose value happens to contain "and". Splitting here would apply
    // filters the query never asked for.
    expect(
      parseQueryToBasicFilters("workflowType in (a) and workflowId='b'"),
    ).toBeNull();
  });

  it("refuses an IN list whose quoting leaves the values ambiguous", () => {
    // Previously produced ["'a", "b'"] — stray quotes, silently wrong.
    expect(parseQueryToBasicFilters("correlationId IN ('a,b')")).toBeNull();
  });

  it("refuses OR, which basic search cannot express", () => {
    expect(
      parseQueryToBasicFilters("status='FAILED' OR status='TIMED_OUT'"),
    ).toBeNull();
  });

  it("refuses a field with no basic control", () => {
    expect(parseQueryToBasicFilters("taskType='HTTP'")).toBeNull();
  });

  it("refuses an operator the field cannot express", () => {
    expect(parseQueryToBasicFilters("workflowId>5")).toBeNull();
    expect(parseQueryToBasicFilters("status<5")).toBeNull();
  });

  it("refuses a parent id filter other than 'no parent'", () => {
    expect(parseQueryToBasicFilters('parentWorkflowId="abc"')).toBeNull();
    expect(parseQueryToBasicFilters('parentWorkflowId=""')).toEqual({
      excludeSubExecutions: true,
    });
  });

  it("refuses grouped clauses", () => {
    expect(
      parseQueryToBasicFilters("(status='FAILED' AND workflowId='a')"),
    ).toBeNull();
  });

  it("refuses anything it cannot fully account for, rather than partly applying", () => {
    // workflowType is understood, taskType is not — nothing is applied.
    expect(
      parseQueryToBasicFilters("workflowType IN (a) AND taskType='HTTP'"),
    ).toBeNull();
  });
});

describe("basicFieldsAfterQueryFormat", () => {
  const noSharedFilters = {
    status: [],
    startTimeFrom: "",
    startTimeTo: "",
    endTimeFrom: "",
    endTimeTo: "",
  };

  it("keeps a status picked in the dropdown when the query says nothing about status", () => {
    // The reported bug: editing the SQL and switching back cleared the status,
    // even though the dropdown is a live control in SQL format and its value
    // was part of the search.
    const next = basicFieldsAfterQueryFormat(
      { workflowType: ["TestWorkflow-Aug"] },
      { ...noSharedFilters, status: ["FAILED"] },
    );

    expect(next.status).toEqual(["FAILED"]);
    expect(next.workflowType).toEqual(["TestWorkflow-Aug"]);
  });

  it("lets a status in the query override the dropdown", () => {
    const next = basicFieldsAfterQueryFormat(
      { status: ["COMPLETED"] },
      { ...noSharedFilters, status: ["FAILED"] },
    );

    expect(next.status).toEqual(["COMPLETED"]);
  });

  it("keeps time bounds the query does not mention", () => {
    const next = basicFieldsAfterQueryFormat(
      { startTimeFrom: "500" },
      {
        ...noSharedFilters,
        startTimeFrom: "100",
        startTimeTo: "200",
        endTimeFrom: "300",
        endTimeTo: "400",
      },
    );

    expect(next.startTimeFrom).toBe("500");
    expect(next.startTimeTo).toBe("200");
    expect(next.endTimeFrom).toBe("300");
    expect(next.endTimeTo).toBe("400");
  });

  it("clears a basic-only field the query no longer mentions", () => {
    // These have no control in SQL format, so the query is their only source:
    // a clause the user deleted should clear the field.
    const next = basicFieldsAfterQueryFormat(
      { workflowId: "abc-123" },
      noSharedFilters,
    );

    expect(next.workflowType).toEqual([]);
    expect(next.correlationIds).toEqual([]);
    expect(next.idempotencyKey).toEqual([]);
    expect(next.modifiedFrom).toBe("");
    expect(next.modifiedTo).toBe("");
    expect(next.excludeSubExecutions).toBe(false);
    expect(next.workflowId).toBe("abc-123");
  });

  it("clears only the basic-only fields when the query is empty", () => {
    const next = basicFieldsAfterQueryFormat(
      {},
      { ...noSharedFilters, status: ["FAILED"], startTimeFrom: "100" },
    );

    expect(next.status).toEqual(["FAILED"]);
    expect(next.startTimeFrom).toBe("100");
    expect(next.workflowType).toEqual([]);
  });

  it("carries a full round-trip back unchanged", () => {
    const filters = {
      workflowType: ["TestWorkflow-Aug"],
      workflowId: "abc-123",
      correlationIds: ["c1"],
      idempotencyKey: ["k1"],
      modifiedFrom: "100",
      modifiedTo: "200",
      excludeSubExecutions: true,
    };
    const parsed = parseQueryToBasicFilters(basicOnlyFilterQuery(filters));

    expect(basicFieldsAfterQueryFormat(parsed!, noSharedFilters)).toMatchObject(
      filters,
    );
  });
});
