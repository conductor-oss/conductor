import { describe, expect, it } from "vitest";
import { generatedFrom } from "./generatedFrom";

describe("generatedFrom", () => {
  it("names the code a native definition is generated from", () => {
    expect(
      generatedFrom({
        name: "loop_shapes",
        metadata: {
          native: { class: "loops.LoopShapes", method: "run" },
        },
      }),
    ).toBe("loops.LoopShapes.run");
    expect(generatedFrom({ name: "orders", metadata: { native: {} } })).toBe(
      "orders",
    );
  });

  it("prefers the source the SDK names, in its language's notation", () => {
    expect(
      generatedFrom({
        name: "orders",
        metadata: {
          native: {
            source: "orders.flows:ship",
            class: "ignored",
            method: "ignored",
          },
        },
      }),
    ).toBe("orders.flows:ship");
  });

  it("is nothing for a definition edited here", () => {
    expect(generatedFrom({ name: "plain", metadata: {} })).toBeUndefined();
    expect(generatedFrom(undefined)).toBeUndefined();
  });
});
