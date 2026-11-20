import { describe, expect, it } from "vitest";
import { parseSamplePayload, payloadFieldPaths } from "./payloadFields";

describe("payloadFieldPaths", () => {
  it("lists top-level and nested keys as ${...} references", () => {
    expect(
      payloadFieldPaths({
        workflowInstanceId: "wf-1",
        customer: { id: "c1", address: { city: "SF" } },
      }),
    ).toEqual([
      "${workflowInstanceId}",
      "${customer}",
      "${customer.id}",
      "${customer.address}",
      "${customer.address.city}",
    ]);
  });

  it("describes arrays by their first element", () => {
    expect(payloadFieldPaths({ items: [{ sku: "a" }, { qty: 2 }] })).toEqual([
      "${items}",
      "${items[0].sku}",
    ]);
  });

  it("skips keys that are not plain identifiers", () => {
    expect(payloadFieldPaths({ "x-id": 1, ok: 2, "has space": 3 })).toEqual([
      "${ok}",
    ]);
  });

  it("stops at a bounded number of fields", () => {
    const big = Object.fromEntries(
      Array.from({ length: 500 }, (_, i) => [`k${i}`, i]),
    );
    expect(payloadFieldPaths(big)).toHaveLength(200);
  });
});

describe("parseSamplePayload", () => {
  it("returns no fields and no error for an empty sample", () => {
    expect(parseSamplePayload("  ")).toEqual({ fields: [] });
  });

  it("reports invalid JSON without throwing", () => {
    expect(parseSamplePayload("{ orderId: ")).toEqual({
      fields: [],
      error: "Not valid JSON yet.",
    });
  });

  it.each(["[1, 2]", '"text"', "42", "null"])(
    "rejects a non-object sample: %s",
    (text) => {
      expect(parseSamplePayload(text).error).toBe(
        "The sample payload must be a JSON object.",
      );
    },
  );

  it("parses an object sample into fields", () => {
    expect(parseSamplePayload('{"orderId":"A1"}')).toEqual({
      fields: ["${orderId}"],
    });
  });
});
