import {
  exactClause,
  mergeValues,
  parseSearchInput,
  splitFreeText,
  splitList,
} from "./searchScopes";

describe("parseSearchInput", () => {
  it("splits pasted ids on commas and whitespace", () => {
    expect(parseSearchInput({}, " a, b  c,,d\n")).toEqual(["a", "b", "c", "d"]);
  });

  it("splits free text on whitespace only", () => {
    expect(
      parseSearchInput({ matchesWords: true }, "  order,123   timeout "),
    ).toEqual(["order,123", "timeout"]);
  });

  it("returns nothing for blank input", () => {
    expect(parseSearchInput({}, "   ")).toEqual([]);
  });
});

describe("mergeValues", () => {
  it("appends new values and skips ones already present", () => {
    expect(mergeValues(["a", "b"], ["b", "c", "c"])).toEqual(["a", "b", "c"]);
  });
});

describe("exact-match list helpers", () => {
  it("reads a comma-separated URL value, including a single legacy id", () => {
    expect(splitList("")).toEqual([]);
    expect(splitList("abc")).toEqual(["abc"]);
    expect(splitList("abc, def")).toEqual(["abc", "def"]);
  });

  it("uses an equality clause for one id and IN for several", () => {
    expect(exactClause("workflowId", [])).toBeNull();
    expect(exactClause("workflowId", ["abc"])).toBe("workflowId='abc'");
    expect(exactClause("workflowId", ["abc", "def"])).toBe(
      "workflowId IN (abc,def)",
    );
  });
});

describe("splitFreeText", () => {
  it("turns the freeText param into its words", () => {
    expect(splitFreeText("")).toEqual([]);
    expect(splitFreeText(" payment  failed ")).toEqual(["payment", "failed"]);
  });
});
