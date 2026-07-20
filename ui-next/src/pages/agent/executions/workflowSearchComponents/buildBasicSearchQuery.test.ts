import { describe, expect, it } from "vitest";
import { dateToEpoch } from "utils";
import {
  buildBasicSearchQuery,
  isWorkflowIdQuery,
  mergeUniqueSortedNames,
} from "./buildBasicSearchQuery";

const emptyFilters = {
  selectedTypes: [] as string[],
  knownAgentNames: [] as string[],
  status: [] as string[],
  startTimeFrom: "",
  startTimeTo: "",
  endTimeFrom: "",
  endTimeTo: "",
  modifiedFrom: "",
  modifiedTo: "",
};

describe("isWorkflowIdQuery", () => {
  it("detects a standalone workflowId equality clause", () => {
    expect(isWorkflowIdQuery("workflowId='abc'")).toBe(true);
  });

  it("detects workflowId ANDed with other clauses", () => {
    expect(
      isWorkflowIdQuery("status IN (COMPLETED) AND workflowId='abc'"),
    ).toBe(true);
  });

  it("ignores queries without workflowId=", () => {
    expect(isWorkflowIdQuery("workflowType IN ('bot')")).toBe(false);
    expect(isWorkflowIdQuery("")).toBe(false);
  });
});

describe("mergeUniqueSortedNames", () => {
  it("dedupes and sorts case-insensitively", () => {
    expect(
      mergeUniqueSortedNames(["zeta", "Alpha"], ["alpha", "beta"]),
    ).toEqual(["Alpha", "alpha", "beta", "zeta"]);
  });

  it("drops empty strings", () => {
    expect(mergeUniqueSortedNames(["a", ""], ["b"])).toEqual(["a", "b"]);
  });
});

describe("buildBasicSearchQuery", () => {
  it("returns empty query and freeText * with no filters", () => {
    expect(
      buildBasicSearchQuery({
        searchInput: "",
        ...emptyFilters,
      }),
    ).toEqual({ query: "", freeText: "*" });
  });

  it("routes a full UUID to workflowId only and ignores other filters", () => {
    const id = "baecfd5f-8462-11f1-b677-26be8ee7cf9f";
    expect(
      buildBasicSearchQuery({
        searchInput: id,
        ...emptyFilters,
        selectedTypes: ["payment-bot"],
        status: ["COMPLETED"],
        startTimeFrom: "1700000000000",
      }),
    ).toEqual({
      query: `workflowId='${id}'`,
      freeText: "*",
    });
  });

  it("ANDs agent, status, and startTime with freeText from combined search", () => {
    const start = "1700000000000";
    const result = buildBasicSearchQuery({
      searchInput: "correlation123",
      ...emptyFilters,
      selectedTypes: ["payment-bot"],
      status: ["RUNNING", "COMPLETED"],
      startTimeFrom: start,
    });

    expect(result.freeText).toBe("correlation123");
    expect(result.query).toBe(
      [
        "workflowType IN ('payment-bot')",
        "status IN (RUNNING,COMPLETED)",
        `startTime>${dateToEpoch(start)}`,
      ].join(" AND "),
    );
  });

  it("routes known agent substring from search input to workflowType", () => {
    expect(
      buildBasicSearchQuery({
        searchInput: "aa",
        ...emptyFilters,
        knownAgentNames: ["aaaa", "payment-bot"],
      }),
    ).toEqual({
      query: "workflowType IN ('aaaa')",
      freeText: "*",
    });
  });
});
