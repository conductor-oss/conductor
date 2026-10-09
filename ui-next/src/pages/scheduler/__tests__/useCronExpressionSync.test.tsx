import { renderHook } from "@testing-library/react";
import { StrictMode } from "react";
import { describe, expect, it } from "vitest";
import { useCronExpression } from "../hooks/useCronExpression";

/**
 * The hook is seeded from its arguments and has to keep following them. Several rows of the
 * scheduler share it, and removing a row hands the row below it a different expression without
 * remounting anything — if the sync misses that, the row validates an expression it no longer
 * shows.
 */
describe("useCronExpression prop sync", () => {
  const render = (expression: string, timezone = "UTC") =>
    renderHook(
      ({ e, tz }: { e: string; tz: string }) => useCronExpression(e, tz),
      { initialProps: { e: expression, tz: timezone }, wrapper: StrictMode },
    );

  it("rehumanizes when the expression changes", () => {
    const { result, rerender } = render("0 0 18 * * ?");
    expect(result.current.humanizedExpression).toBe("At 06:00 PM");

    rerender({ e: "0 30 7 * * ?", tz: "UTC" });

    expect(result.current.cronExpression).toBe("0 30 7 * * ?");
    expect(result.current.humanizedExpression).toBe("At 07:30 AM");
  });

  it("reports an expression that becomes invalid", () => {
    const { result, rerender } = render("0 0 18 * * ?");
    expect(result.current.cronError).toBeUndefined();

    rerender({ e: "bogus cron", tz: "UTC" });

    expect(result.current.cronError).toBeDefined();
  });

  it("recalculates the next runs when only the timezone changes", () => {
    const { result, rerender } = render("0 0 18 * * ?");
    const before = result.current.futureMatches[0];

    rerender({ e: "0 0 18 * * ?", tz: "Asia/Kolkata" });

    expect(result.current.futureMatches[0]).not.toBe(before);
  });
});
