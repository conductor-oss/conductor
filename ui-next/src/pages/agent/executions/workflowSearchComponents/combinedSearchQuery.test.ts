import { describe, expect, it } from "vitest";
import {
  isUnsafeForFreeText,
  matchAgentNames,
  quoteQueryValue,
  resolveCombinedSearch,
  workflowIdClause,
  workflowTypeClause,
} from "./combinedSearchQuery";

const KNOWN_AGENTS = ["aaaa", "payment-bot", "my-agent", "@!@&/2/"];

describe("isUnsafeForFreeText", () => {
  it("flags characters that break postgres to_tsquery", () => {
    expect(isUnsafeForFreeText("@!@&/2/")).toBe(true);
    expect(isUnsafeForFreeText("my-agent")).toBe(true);
    expect(isUnsafeForFreeText("order.123")).toBe(true);
  });

  it("allows plain alphanumeric free text", () => {
    expect(isUnsafeForFreeText("correlation123")).toBe(false);
    expect(isUnsafeForFreeText("hello world")).toBe(false);
    expect(isUnsafeForFreeText("idempotency_key")).toBe(false);
  });
});

describe("matchAgentNames", () => {
  it("matches substring agent names case-insensitively", () => {
    expect(matchAgentNames("aa", KNOWN_AGENTS)).toEqual(["aaaa"]);
    expect(matchAgentNames("PAY", KNOWN_AGENTS)).toEqual(["payment-bot"]);
  });

  it("matches wildcard patterns against known agents", () => {
    expect(matchAgentNames("my-*", KNOWN_AGENTS)).toEqual(["my-agent"]);
    expect(matchAgentNames("*bot*", KNOWN_AGENTS)).toEqual(["payment-bot"]);
  });

  it("returns empty when nothing matches", () => {
    expect(matchAgentNames("zzz", KNOWN_AGENTS)).toEqual([]);
  });
});

describe("workflowTypeClause / workflowIdClause", () => {
  it("quotes workflowType values for the query DSL", () => {
    expect(workflowTypeClause(["@!@&/2/"])).toBe("workflowType IN ('@!@&/2/')");
  });

  it("keeps a single wildcard as equality/LIKE form", () => {
    expect(workflowTypeClause(["my-*"])).toBe("workflowType=my-*");
  });

  it("quotes exact workflowId values", () => {
    expect(workflowIdClause("ABCDEF12-3456-7890-abcd-ef1234567890")).toBe(
      "workflowId='abcdef12-3456-7890-abcd-ef1234567890'",
    );
  });

  it("strips single quotes from quoted values", () => {
    expect(quoteQueryValue("a'b")).toBe("'ab'");
  });
});

describe("resolveCombinedSearch", () => {
  it("returns freeText * and no clauses for empty input", () => {
    expect(resolveCombinedSearch("")).toEqual({
      searchClauses: [],
      freeText: "*",
      isExecutionIdSearch: false,
    });
    expect(resolveCombinedSearch("   ")).toEqual({
      searchClauses: [],
      freeText: "*",
      isExecutionIdSearch: false,
    });
  });

  it("routes a full UUID to an exact workflowId clause", () => {
    const id = "20c3f74f-71bb-4b59-8a61-f261b94123ef";
    expect(resolveCombinedSearch(id)).toEqual({
      searchClauses: [`workflowId='${id}'`],
      freeText: "*",
      isExecutionIdSearch: true,
    });
  });

  it("does not treat partial UUIDs as execution-id search", () => {
    // Partial ids are unsupported — fall through to freeText when safe.
    expect(resolveCombinedSearch("abcdef12")).toEqual({
      searchClauses: [],
      freeText: "abcdef12",
      isExecutionIdSearch: false,
    });
  });

  it("routes substring matches to known agents as workflowType", () => {
    expect(
      resolveCombinedSearch("aa", { knownAgentNames: KNOWN_AGENTS }),
    ).toEqual({
      searchClauses: ["workflowType IN ('aaaa')"],
      freeText: "*",
      isExecutionIdSearch: false,
    });
  });

  it("routes special-character agent names to workflowType, not freeText", () => {
    expect(
      resolveCombinedSearch("@!@&/2/", { knownAgentNames: KNOWN_AGENTS }),
    ).toEqual({
      searchClauses: ["workflowType IN ('@!@&/2/')"],
      freeText: "*",
      isExecutionIdSearch: false,
    });
  });

  it("routes unknown special-character terms to workflowType", () => {
    // Not in known list — still must not go to freeText/to_tsquery.
    expect(resolveCombinedSearch("@weird/name")).toEqual({
      searchClauses: ["workflowType IN ('@weird/name')"],
      freeText: "*",
      isExecutionIdSearch: false,
    });
  });

  it("routes agent wildcards with no list hits to workflowType LIKE", () => {
    expect(resolveCombinedSearch("my-*")).toEqual({
      searchClauses: ["workflowType=my-*"],
      freeText: "*",
      isExecutionIdSearch: false,
    });
  });

  it("routes plain alphanumeric terms to freeText", () => {
    expect(resolveCombinedSearch("correlation123")).toEqual({
      searchClauses: [],
      freeText: "correlation123",
      isExecutionIdSearch: false,
    });
  });

  it("does not override an existing agent dropdown filter with agent routing", () => {
    // Dropdown already selected agents — search becomes freeText when safe.
    expect(
      resolveCombinedSearch("correlation123", {
        selectedTypes: ["payment-bot"],
        knownAgentNames: KNOWN_AGENTS,
      }),
    ).toEqual({
      searchClauses: [],
      freeText: "correlation123",
      isExecutionIdSearch: false,
    });
  });

  it("still routes full UUID searches when an agent dropdown filter is set", () => {
    const id = "20c3f74f-71bb-4b59-8a61-f261b94123ef";
    expect(
      resolveCombinedSearch(id, { selectedTypes: ["payment-bot"] }),
    ).toEqual({
      searchClauses: [`workflowId='${id}'`],
      freeText: "*",
      isExecutionIdSearch: true,
    });
  });

  it("drops unsafe freeText when an agent dropdown filter is already set", () => {
    expect(
      resolveCombinedSearch("@!@&/2/", { selectedTypes: ["payment-bot"] }),
    ).toEqual({
      searchClauses: [],
      freeText: "*",
      isExecutionIdSearch: false,
    });
  });

  it("trims whitespace before routing", () => {
    expect(resolveCombinedSearch("  correlation123  ")).toEqual({
      searchClauses: [],
      freeText: "correlation123",
      isExecutionIdSearch: false,
    });
  });
});
