import { replaceValues } from "../object";

describe("replaceValues", () => {
  it("should replace values in an object", () => {
    const obj = { a: "a", b: "b", c: "c" };
    const expected = { a: "a", b: "b", c: "d" };
    expect(replaceValues(obj, "c", "d")).toEqual(expected);
  });
  it("should replace values in an object with nested objects", () => {
    const obj = { a: "a", b: "b", c: { d: "d", e: "e" } };
    const expected = { a: "a", b: "b", c: { d: "d", e: "f" } };
    expect(replaceValues(obj, "e", "f")).toEqual(expected);
  });
  it("should replace values in an object with nested arrays", () => {
    const obj = { a: "a", b: "b", c: ["d", "e"] };
    const expected = { a: "a", b: "b", c: ["d", "f"] };
    expect(replaceValues(obj, "e", "f")).toEqual(expected);
  });
  it("should replace values in an object with nested arrays and objects", () => {
    const obj = { a: "a", b: "b", c: ["d", { e: "e" }] };
    const expected = { a: "a", b: "b", c: ["d", { e: "f" }] };
    expect(replaceValues(obj, "e", "f")).toEqual(expected);
  });
});
