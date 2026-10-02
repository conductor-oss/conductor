import {
  mergeValues,
  parseSearchInput,
  splitFreeText,
  splitWorkflowIds,
  workflowIdClause,
} from "./searchScopes";

describe("parseSearchInput", () => {
  it("splits pasted ids on commas and whitespace", () => {
    expect(parseSearchInput("correlationId", " a, b  c,,d\n")).toEqual([
      "a",
      "b",
      "c",
      "d",
    ]);
  });

  it("splits free text on whitespace only", () => {
    expect(parseSearchInput("freeText", "  order,123   timeout ")).toEqual([
      "order,123",
      "timeout",
    ]);
  });

  it("returns nothing for blank input", () => {
    expect(parseSearchInput("workflowId", "   ")).toEqual([]);
  });
});

describe("mergeValues", () => {
  it("appends new values and skips ones already present", () => {
    expect(mergeValues(["a", "b"], ["b", "c", "c"])).toEqual(["a", "b", "c"]);
  });
});

describe("workflow id helpers", () => {
  it("reads the comma-separated URL value, including a single legacy id", () => {
    expect(splitWorkflowIds("")).toEqual([]);
    expect(splitWorkflowIds("abc")).toEqual(["abc"]);
    expect(splitWorkflowIds("abc, def")).toEqual(["abc", "def"]);
  });

  it("uses an equality clause for one id and IN for several", () => {
    expect(workflowIdClause([])).toBeNull();
    expect(workflowIdClause(["abc"])).toBe("workflowId='abc'");
    expect(workflowIdClause(["abc", "def"])).toBe("workflowId IN (abc,def)");
  });
});

describe("splitFreeText", () => {
  it("turns the freeText param into its words", () => {
    expect(splitFreeText("")).toEqual([]);
    expect(splitFreeText(" payment  failed ")).toEqual(["payment", "failed"]);
  });
});
