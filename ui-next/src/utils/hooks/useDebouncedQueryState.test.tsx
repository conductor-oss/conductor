/**
 * Typing in search editors used to drop characters: every keystroke wrote the
 * URL, and the re-render handed the controlled editor a stale value that
 * overwrote what had been typed since. These tests pin the two halves of the
 * fix — reads stay immediate, URL writes are debounced — without which the
 * drop returns or a search sends half-typed text.
 */
import React from "react";
import { act, renderHook } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useDebouncedQueryState } from "./useDebouncedQueryState";

const DELAY = 300;

const renderQueryState = (initialUrl = "/") => {
  const seen = { search: "" };
  const wrapper = ({ children }: { children?: React.ReactNode }) => (
    <MemoryRouter initialEntries={[initialUrl]}>{children}</MemoryRouter>
  );

  const view = renderHook(
    () => {
      seen.search = useLocation().search;
      return useDebouncedQueryState("fullTextQuery", "", DELAY);
    },
    { wrapper },
  );

  return { ...view, url: seen };
};

/** One keystroke: the editor reports its whole buffer on every change. */
const type = (
  result: { current: [string, (value: string) => void] },
  text: string,
) => act(() => result.current[1](text));

describe("useDebouncedQueryState", () => {
  beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
  afterEach(() => vi.useRealTimers());

  it("reports every character as it is typed", () => {
    const { result } = renderQueryState();

    type(result, "a");
    type(result, "ap");
    type(result, "app");

    expect(result.current[0]).toBe("app");
  });

  it("keeps the last keystroke when the URL write lands mid-word", () => {
    // The regression: the deferred write re-renders the page, and anything
    // typed while it was in flight must survive that render.
    const { result } = renderQueryState();

    type(result, "appro");
    act(() => vi.advanceTimersByTime(DELAY));
    type(result, "approve");

    expect(result.current[0]).toBe("approve");
    act(() => vi.advanceTimersByTime(DELAY));
    expect(result.current[0]).toBe("approve");
  });

  it("writes the URL once typing pauses, not once per keystroke", () => {
    const { result, url } = renderQueryState();

    type(result, "a");
    type(result, "ap");
    type(result, "approve");
    expect(url.search).toBe("");

    act(() => vi.advanceTimersByTime(DELAY));

    expect(url.search).toBe("?fullTextQuery=approve");
  });

  it("starts from the value already in the URL", () => {
    const { result } = renderQueryState("/?fullTextQuery=approve+OR+reject");

    expect(result.current[0]).toBe("approve OR reject");
  });

  it("clears immediately when Reset empties the field", () => {
    const { result, url } = renderQueryState("/?fullTextQuery=approve");

    type(result, "");

    expect(result.current[0]).toBe("");
    act(() => vi.advanceTimersByTime(DELAY));
    expect(url.search).toBe("");
  });

  it("does not resurrect the old text after its own write lands", () => {
    const { result } = renderQueryState("/?fullTextQuery=approve");

    type(result, "reject");
    act(() => vi.advanceTimersByTime(DELAY));

    expect(result.current[0]).toBe("reject");
  });

  it("stops a queued write when the field unmounts", () => {
    const { result, unmount } = renderQueryState();

    type(result, "approve");
    unmount();

    // A write after unmount would navigate on behalf of a page that is gone.
    expect(() => act(() => vi.advanceTimersByTime(DELAY))).not.toThrow();
  });
});
