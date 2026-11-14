import { describe, expect, it } from "vitest";
import { generatedFrom } from "./generatedFrom";

describe("generatedFrom", () => {
  it("names the code a native definition is generated from", () => {
    expect(
      generatedFrom({
        name: "loop_shapes",
        metadata: {
          native: { class: "loops.LoopShapes", method: "run", codeType: "x" },
        },
      }),
    ).toBe("loops.LoopShapes.run");
    expect(
      generatedFrom({ metadata: { native: { codeType: "orders.v1.code" } } }),
    ).toBe("orders.v1.code");
  });

  it("is nothing for a definition edited here", () => {
    expect(generatedFrom({ name: "plain", metadata: {} })).toBeUndefined();
    expect(generatedFrom(undefined)).toBeUndefined();
  });
});
