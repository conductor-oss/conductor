import { act, renderHook } from "@testing-library/react";
import { SearchScope } from "./searchScopes";
import { useScopedSearch } from "./useScopedSearch";

const setup = (values: Partial<Record<SearchScope, string[]>> = {}) => {
  const setValues = vi.fn();
  const onSearchAgain = vi.fn();
  const { result } = renderHook(() =>
    useScopedSearch({
      values: {
        workflowId: [],
        correlationId: [],
        idempotencyKey: [],
        freeText: [],
        ...values,
      },
      setValues,
      onSearchAgain,
    }),
  );
  return { result, setValues, onSearchAgain };
};

describe("useScopedSearch", () => {
  it("adds typed values to the chosen field and clears the input", () => {
    const { result, setValues } = setup({ correlationId: ["a"] });

    act(() => result.current.setScope("correlationId"));
    act(() => result.current.setTerm("a, b c"));
    act(() => result.current.submit());

    expect(setValues).toHaveBeenCalledWith("correlationId", ["a", "b", "c"]);
    expect(result.current.term).toBe("");
  });

  it("searches again when submitting would not change any filter", () => {
    const { result, setValues, onSearchAgain } = setup({ workflowId: ["x"] });

    act(() => result.current.submit());
    act(() => result.current.setTerm("x"));
    act(() => result.current.submit());

    expect(onSearchAgain).toHaveBeenCalledTimes(2);
    expect(setValues).not.toHaveBeenCalled();
  });

  it("builds one chip per field that has values, in field order", () => {
    const { result } = setup({
      freeText: ["timeout"],
      workflowId: ["x", "y"],
    });

    expect(result.current.chips).toEqual([
      { scope: "workflowId", values: ["x", "y"] },
      { scope: "freeText", values: ["timeout"] },
    ]);
  });
});
