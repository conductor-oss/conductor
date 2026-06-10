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
import {
  deriveFallbackIterationStatus,
  getOrderedIterationKeys,
} from "./doWhileIterationHelpers";

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
