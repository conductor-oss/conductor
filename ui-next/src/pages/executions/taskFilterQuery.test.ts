/**
 * Toggling "SQL format" on used to leave the query box empty, dropping every
 * filter that only basic search has a control for — from the box and from the
 * request. These pin the clause formats that carry them across.
 */
import { describe, expect, it } from "vitest";
import {
  basicOnlyTaskFilterClauses,
  basicOnlyTaskFilterQuery,
  basicTaskFieldsAfterQueryFormat,
  parseQueryToBasicTaskFilters,
} from "./taskFilterQuery";

const NO_TIMES = {
  startTimeFrom: "",
  startTimeTo: "",
  endTimeFrom: "",
  endTimeTo: "",
};

describe("basicOnlyTaskFilterClauses", () => {
  it("returns nothing when no basic-only filter is set", () => {
    expect(basicOnlyTaskFilterClauses({})).toEqual([]);
    expect(
      basicOnlyTaskFilterClauses({
        taskDefName: "",
        taskType: [],
        taskId: "",
        taskRefName: "",
        workflowName: "",
        status: [],
      }),
    ).toEqual([]);
  });

  it("quotes the single-value fields", () => {
    expect(
      basicOnlyTaskFilterClauses({
        taskDefName: "send_email",
        taskId: "abc-123",
        taskRefName: "send_email_ref",
        workflowName: "TestWorkflow-Aug",
      }),
    ).toEqual([
      "taskDefName='send_email'",
      "taskId='abc-123'",
      "referenceTaskName='send_email_ref'",
      "workflowName='TestWorkflow-Aug'",
    ]);
  });

  it("comma-separates the multi-value fields", () => {
    expect(
      basicOnlyTaskFilterClauses({
        taskType: ["SIMPLE", "HTTP"],
        status: ["FAILED"],
      }),
    ).toEqual(["taskType IN (SIMPLE,HTTP)", "status IN (FAILED)"]);
  });

  it("joins every clause with AND", () => {
    expect(
      basicOnlyTaskFilterQuery({
        taskDefName: "send_email",
        status: ["FAILED", "TIMED_OUT"],
      }),
    ).toBe("taskDefName='send_email' AND status IN (FAILED,TIMED_OUT)");
  });

  it("produces an empty query when nothing is filtered", () => {
    expect(basicOnlyTaskFilterQuery({})).toBe("");
  });
});

describe("parseQueryToBasicTaskFilters", () => {
  it("reads back what basicOnlyTaskFilterQuery wrote", () => {
    const filters = {
      taskDefName: "send_email",
      taskType: ["SIMPLE"],
      taskId: "abc-123",
      taskRefName: "send_email_ref",
      workflowName: "TestWorkflow-Aug",
      status: ["FAILED"],
    };
    expect(
      parseQueryToBasicTaskFilters(basicOnlyTaskFilterQuery(filters)),
    ).toEqual(filters);
  });

  it("treats an empty query as no filters", () => {
    expect(parseQueryToBasicTaskFilters("")).toEqual({});
    expect(parseQueryToBasicTaskFilters("   ")).toEqual({});
  });

  it("reads the time bounds advanced search also controls", () => {
    expect(
      parseQueryToBasicTaskFilters(
        "startTime>100 AND startTime<200 AND endTime>300 AND endTime<400",
      ),
    ).toEqual({
      startTimeFrom: "100",
      startTimeTo: "200",
      endTimeFrom: "300",
      endTimeTo: "400",
    });
  });

  it("accepts a lower case IN, which is what the UI's own example uses", () => {
    expect(parseQueryToBasicTaskFilters("status in (FAILED)")).toEqual({
      status: ["FAILED"],
    });
  });

  it("refuses a query basic search cannot express", () => {
    // OR has no equivalent in the form.
    expect(
      parseQueryToBasicTaskFilters(
        "status IN (FAILED) OR status IN (COMPLETED)",
      ),
    ).toBeNull();
    // A field with no control.
    expect(parseQueryToBasicTaskFilters("correlationId='c1'")).toBeNull();
    // An operator the field does not support.
    expect(parseQueryToBasicTaskFilters("taskDefName>send_email")).toBeNull();
    // Not a clause at all.
    expect(parseQueryToBasicTaskFilters("send_email")).toBeNull();
  });

  it("refuses the whole query when one clause is unrecognised", () => {
    expect(
      parseQueryToBasicTaskFilters("taskDefName='send_email' AND version=2"),
    ).toBeNull();
  });
});

describe("basicTaskFieldsAfterQueryFormat", () => {
  it("clears a basic-only field the query no longer mentions", () => {
    expect(
      basicTaskFieldsAfterQueryFormat({ taskId: "abc-123" }, NO_TIMES),
    ).toEqual({
      taskDefName: "",
      taskType: [],
      taskId: "abc-123",
      taskRefName: "",
      workflowName: "",
      status: [],
      ...NO_TIMES,
    });
  });

  it("keeps the time controls the query says nothing about", () => {
    const current = {
      startTimeFrom: "100",
      startTimeTo: "200",
      endTimeFrom: "300",
      endTimeTo: "400",
    };
    expect(basicTaskFieldsAfterQueryFormat({}, current)).toMatchObject(current);
  });

  it("lets the query text override the time controls", () => {
    expect(
      basicTaskFieldsAfterQueryFormat(
        { startTimeFrom: "999" },
        { ...NO_TIMES, startTimeFrom: "100" },
      ),
    ).toMatchObject({ startTimeFrom: "999" });
  });
});
