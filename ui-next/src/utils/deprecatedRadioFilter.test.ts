import { describe, expect, it } from "vitest";
import { filterOptionByEvaluatorType } from "./deprecatedRadioFilter";

const values = (evaluatorType?: string) =>
  filterOptionByEvaluatorType(evaluatorType).map((o) => o.value);

describe("filterOptionByEvaluatorType", () => {
  it("offers the evaluators a switch can be given", () => {
    expect(values("graaljs")).toEqual(["graaljs", "value-param"]);
  });

  it("shows native code, not to be chosen, on a native workflow's switch", () => {
    expect(values("native-code")).toEqual([
      "graaljs",
      "value-param",
      "native-code",
    ]);
    expect(
      filterOptionByEvaluatorType("native-code").find(
        (o) => o.value === "native-code",
      )?.disabled,
    ).toBe(true);
  });
});
