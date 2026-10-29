import { describeOutcome } from "./deleteOutcome";

// Newest first, the order the dialog hands them over in.
const THREE = [3, 2, 1];
const MANY = [6, 5, 4, 3, 2, 1];

describe("describeOutcome", () => {
  it("says nothing survives a delete-all", () => {
    expect(describeOutcome(THREE, undefined, "schema")).toBe(
      "Nothing will remain — the schema itself is removed.",
    );
  });

  it("names the survivors and the new latest", () => {
    expect(describeOutcome(THREE, 3, "workflow")).toBe(
      "Versions 2 and 1 will remain, and version 2 becomes the latest.",
    );
  });

  it("leaves latest alone when an older version goes", () => {
    expect(describeOutcome(THREE, 1, "workflow")).toBe(
      "Versions 3 and 2 will remain.",
    );
  });

  it("uses the singular for a lone survivor", () => {
    expect(describeOutcome([2, 1], 1, "prompt")).toBe("Version 2 will remain.");
  });

  it("does not name the lone survivor twice", () => {
    expect(describeOutcome([2, 1], 2, "prompt")).toBe(
      "Version 1 will remain and become the latest.",
    );
  });

  it("counts them instead of listing once there are more than three", () => {
    expect(describeOutcome(MANY, 6, "user form")).toBe(
      "5 earlier versions will remain, and version 5 becomes the latest.",
    );
  });

  it("calls them other, not earlier, when the newest is staying", () => {
    expect(describeOutcome(MANY, 1, "user form")).toBe(
      "5 other versions will remain.",
    );
  });

  it("says the definition goes when its last version goes", () => {
    expect(describeOutcome([1], 1, "schema")).toBe(
      "This is the only version, so the schema is removed.",
    );
  });
});
