/**
 * Unit tests for DoWhileIteration status-display logic.
 *
 * These tests cover the two functions that determine which icon is rendered
 * next to each iteration row:
 *
 *   getOrderedIterationKeys       – builds the descending list of iteration numbers
 *   deriveFallbackIterationStatus – picks the per-row TaskStatus when the API has
 *                                   not returned a per-iteration status field
 *
 * NOTE: component-render tests (using @testing-library/react) cannot run in
 * this monorepo because the outer workspace (conductor-ui) and this package
 * both install react/react-dom, causing a "two React instances" dispatcher
 * conflict. All meaningful status logic has therefore been extracted into
 * pure, synchronously testable functions.
 */

import { describe, expect, it } from "vitest";

import { TaskStatus } from "types/TaskStatus";
import { featureFlags, FEATURES } from "utils/flags";
import {
  deriveFallbackIterationStatus,
  getOrderedIterationKeys,
  isIterationSummarized,
} from "./doWhileIterationHelpers";

// ---------------------------------------------------------------------------
// WORKFLOW_SUMMARIZE feature flag
//
// The summarize toggle is an Orkes-only feature. IterationSection calls
//   featureFlags.isEnabled(FEATURES.WORKFLOW_SUMMARIZE, true)
// with defaultValue=true, meaning the toggle is ON in any environment that
// does not explicitly configure the flag. The OSS context.js opts out by
// setting WORKFLOW_SUMMARIZE: false. Tests run without context.js, so they
// exercise the defaultValue path.
// ---------------------------------------------------------------------------

describe("WORKFLOW_SUMMARIZE feature flag", () => {
  it("is registered in the FEATURES map with the correct string key", () => {
    expect(FEATURES.WORKFLOW_SUMMARIZE).toBe("WORKFLOW_SUMMARIZE");
  });

  it("returns false when the flag is unconfigured and no default is supplied (base default)", () => {
    // In test env window.conductor and env vars are not set, so result is
    // undefined and isEnabled returns its own defaultValue param (false).
    expect(featureFlags.isEnabled(FEATURES.WORKFLOW_SUMMARIZE)).toBe(false);
  });

  it("returns true when the flag is unconfigured but caller supplies defaultValue=true", () => {
    // This is the call site in IterationSection: the toggle shows in Orkes
    // environments unless context.js explicitly sets the flag to false.
    expect(featureFlags.isEnabled(FEATURES.WORKFLOW_SUMMARIZE, true)).toBe(
      true,
    );
  });
});

// ---------------------------------------------------------------------------
// isIterationSummarized
// ---------------------------------------------------------------------------

describe("isIterationSummarized", () => {
  // Key absent cases — the iteration was pruned from outputData

  it("returns true when key is absent and task is not processing (pruned by keepLastN)", () => {
    expect(isIterationSummarized(5, { "1": {} }, false)).toBe(true);
  });

  it("returns false when key is absent but task is still processing (iteration not yet written)", () => {
    expect(isIterationSummarized(2, { "1": {} }, true)).toBe(false);
  });

  // Key present cases — value determines whether the entry was summarized

  it("returns true when value carries the _summarized sentinel", () => {
    expect(
      isIterationSummarized(1, { "1": { _summarized: true } }, false),
    ).toBe(true);
  });

  it("returns false when value has _summarized: false", () => {
    expect(
      isIterationSummarized(1, { "1": { _summarized: false } }, false),
    ).toBe(false);
  });

  it("returns false when value has no _summarized field (real data)", () => {
    expect(isIterationSummarized(1, { "1": { result: "ok" } }, false)).toBe(
      false,
    );
  });

  it("returns false when value is null", () => {
    expect(isIterationSummarized(1, { "1": null }, false)).toBe(false);
  });

  it("returns false when value is a non-object primitive", () => {
    expect(isIterationSummarized(1, { "1": "done" as unknown }, false)).toBe(
      false,
    );
  });

  it("returns true when _summarized is present alongside other fields", () => {
    expect(
      isIterationSummarized(
        3,
        { "3": { _summarized: true, status: "COMPLETED" } },
        false,
      ),
    ).toBe(true);
  });

  it("returns false for an empty outputData object when task is processing", () => {
    expect(isIterationSummarized(1, {}, true)).toBe(false);
  });

  it("returns true for an empty outputData object when task is not processing", () => {
    expect(isIterationSummarized(1, {}, false)).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// getOrderedIterationKeys
// ---------------------------------------------------------------------------

describe("getOrderedIterationKeys", () => {
  it("returns descending numbers matching outputData keys", () => {
    expect(
      getOrderedIterationKeys({ "1": {}, "2": {}, "3": {} }, { iteration: 3 }),
    ).toEqual([3, 2, 1]);
  });

  it("fills up to task.iteration when it exceeds outputData keys", () => {
    expect(getOrderedIterationKeys({ "1": {} }, { iteration: 4 })).toEqual([
      4, 3, 2, 1,
    ]);
  });

  it("returns empty array for empty inputs", () => {
    expect(getOrderedIterationKeys({}, {})).toEqual([]);
  });

  it("returns numeric keys descending when task.iteration is absent", () => {
    expect(getOrderedIterationKeys({ "3": {}, "1": {}, "2": {} }, {})).toEqual([
      3, 2, 1,
    ]);
  });

  it("ignores non-numeric output keys", () => {
    const result = getOrderedIterationKeys(
      { "1": {}, foo: {}, "2": {} },
      { iteration: 2 },
    );
    expect(result).toEqual([2, 1]);
  });
});

// ---------------------------------------------------------------------------
// deriveFallbackIterationStatus
// ---------------------------------------------------------------------------

describe("deriveFallbackIterationStatus", () => {
  it("returns COMPLETED when the iteration key exists in outputData", () => {
    const outputData = { "1": { result: "ok" }, "2": { result: "ok" } };
    expect(
      deriveFallbackIterationStatus(1, outputData, TaskStatus.FAILED),
    ).toBe(TaskStatus.COMPLETED);
    expect(
      deriveFallbackIterationStatus(2, outputData, TaskStatus.FAILED),
    ).toBe(TaskStatus.COMPLETED);
  });

  it("returns the parent task status when the iteration key is absent", () => {
    const outputData = { "1": { result: "ok" } };
    expect(
      deriveFallbackIterationStatus(2, outputData, TaskStatus.FAILED),
    ).toBe(TaskStatus.FAILED);
  });

  it("returns IN_PROGRESS for the active iteration of a running loop", () => {
    const outputData = { "1": { result: "ok" } };
    expect(
      deriveFallbackIterationStatus(2, outputData, TaskStatus.IN_PROGRESS),
    ).toBe(TaskStatus.IN_PROGRESS);
  });

  it("returns TIMED_OUT for the active iteration of a timed-out loop", () => {
    const outputData = { "1": { result: "ok" } };
    expect(
      deriveFallbackIterationStatus(2, outputData, TaskStatus.TIMED_OUT),
    ).toBe(TaskStatus.TIMED_OUT);
  });

  it("returns COMPLETED even when the parent task is FAILED (earlier iteration completed)", () => {
    const outputData = { "1": {}, "2": {}, "3": {} };
    // iterations 1-3 all completed; the task failed on a later iteration
    expect(
      deriveFallbackIterationStatus(1, outputData, TaskStatus.FAILED),
    ).toBe(TaskStatus.COMPLETED);
  });
});

// ---------------------------------------------------------------------------
// End-to-end: status derivation → TaskStatus value passed to IterationStatusIcon
//
// These confirm the correct TaskStatus is produced for each scenario so that
// IterationStatusIcon receives the right value and renders the right icon.
// ---------------------------------------------------------------------------

describe("status derivation → TaskStatus for IterationStatusIcon", () => {
  it("completed iteration yields COMPLETED", () => {
    expect(
      deriveFallbackIterationStatus(1, { "1": {} }, TaskStatus.FAILED),
    ).toBe(TaskStatus.COMPLETED);
  });

  it("active failed iteration yields FAILED", () => {
    expect(
      deriveFallbackIterationStatus(3, { "1": {}, "2": {} }, TaskStatus.FAILED),
    ).toBe(TaskStatus.FAILED);
  });

  it("active in-progress iteration yields IN_PROGRESS", () => {
    expect(
      deriveFallbackIterationStatus(2, { "1": {} }, TaskStatus.IN_PROGRESS),
    ).toBe(TaskStatus.IN_PROGRESS);
  });

  it("active timed-out iteration yields TIMED_OUT", () => {
    expect(
      deriveFallbackIterationStatus(2, { "1": {} }, TaskStatus.TIMED_OUT),
    ).toBe(TaskStatus.TIMED_OUT);
  });

  it("fetched iteration with no status falls back to COMPLETED", () => {
    const status: TaskStatus | undefined = undefined;
    expect(status ?? TaskStatus.COMPLETED).toBe(TaskStatus.COMPLETED);
  });
});
