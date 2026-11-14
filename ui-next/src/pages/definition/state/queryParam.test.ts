import { describe, it, expect } from "vitest";
import { nextQueryString } from "./queryParam";

describe("nextQueryString", () => {
  it("adds a new param to an empty search", () => {
    expect(nextQueryString("", "taskReferenceName", "simple_ref")).toEqual(
      "taskReferenceName=simple_ref",
    );
  });

  it("adds a param while preserving existing ones", () => {
    expect(nextQueryString("?foo=bar", "taskReferenceName", "ref")).toEqual(
      "foo=bar&taskReferenceName=ref",
    );
  });

  it("updates an existing param's value", () => {
    expect(
      nextQueryString("?taskReferenceName=old", "taskReferenceName", "new"),
    ).toEqual("taskReferenceName=new");
  });

  it("removes a param when the value is empty", () => {
    expect(
      nextQueryString(
        "?taskReferenceName=ref&foo=bar",
        "taskReferenceName",
        "",
      ),
    ).toEqual("foo=bar");
  });

  it("removes a param when the value is undefined", () => {
    expect(
      nextQueryString("?taskReferenceName=ref", "taskReferenceName", undefined),
    ).toEqual("");
  });

  it("returns null (skip navigation) when setting the same value", () => {
    expect(
      nextQueryString("?taskReferenceName=ref", "taskReferenceName", "ref"),
    ).toBeNull();
  });

  it("returns null (skip navigation) when clearing a param that is absent", () => {
    // The dismissImportSuccessfullParam case: clearing showImportSuccess when
    // it isn't present must not trigger a navigation.
    expect(nextQueryString("?foo=bar", "showImportSuccess", "")).toBeNull();
  });

  it("returns null when clearing on an empty search", () => {
    expect(nextQueryString("", "showImportSuccess", "")).toBeNull();
  });
});
