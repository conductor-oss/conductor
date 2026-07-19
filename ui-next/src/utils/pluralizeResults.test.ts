import { describe, expect, it } from "vitest";

import { createTableTitle, pluralizeResults } from "./helpers";

describe("pluralizeResults", () => {
  it("singularizes a count of one", () => {
    expect(pluralizeResults(1)).toBe("1 result");
  });

  it("pluralizes zero and counts greater than one", () => {
    expect(pluralizeResults(0)).toBe("0 results");
    expect(pluralizeResults(2)).toBe("2 results");
  });
});

describe("createTableTitle", () => {
  it("singularizes when a single row is shown", () => {
    expect(createTableTitle({ filteredData: [1], data: [1] })).toBe(
      "1 result",
    );
  });

  it("pluralizes and reports hidden rows when filtered", () => {
    expect(
      createTableTitle({ filteredData: [1], data: [1, 2, 3] }),
    ).toBe("1 result (2 not shown)");
    expect(
      createTableTitle({ filteredData: [1, 2], data: [1, 2] }),
    ).toBe("2 results");
  });
});
