import { describe, expect, it } from "vitest";
import {
  buildSuggestions,
  fillIterationPlaceholders,
  pageStartForIteration,
} from "./iterationHelpers";

// ---------------------------------------------------------------------------
// buildSuggestions
// ---------------------------------------------------------------------------

describe("buildSuggestions", () => {
  it("returns exact match first, then prefix-scaled numbers", () => {
    expect(buildSuggestions("5", 350)).toEqual([5, 50, 51, 52, 53, 54]);
  });

  it("caps results at 6 entries", () => {
    expect(buildSuggestions("1", 999).length).toBeLessThanOrEqual(6);
  });

  it("includes exact match even when no scaled values exist", () => {
    expect(buildSuggestions("35", 35)).toEqual([35]);
  });

  it("includes both exact and one scaled match when max is just large enough", () => {
    expect(buildSuggestions("35", 350)).toEqual([35, 350]);
  });

  it("returns empty for prefix 0", () => {
    expect(buildSuggestions("0", 350)).toEqual([]);
  });

  it("returns empty when prefix exceeds max", () => {
    expect(buildSuggestions("400", 350)).toEqual([]);
  });

  it("returns empty for non-numeric prefix", () => {
    expect(buildSuggestions("abc", 350)).toEqual([]);
  });

  it("returns empty for empty prefix", () => {
    expect(buildSuggestions("", 350)).toEqual([]);
  });

  it("handles single-digit prefix against small max", () => {
    expect(buildSuggestions("3", 30)).toEqual([3, 30]);
  });

  it("does not include numbers beyond max in scaled results", () => {
    const results = buildSuggestions("1", 15);
    expect(results.every((n) => n <= 15)).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// pageStartForIteration
// ---------------------------------------------------------------------------

describe("pageStartForIteration", () => {
  const PAGE_SIZE = 50;

  it("returns the correct page start for a low iteration number", () => {
    // iteration 5 of 350: index = 350-5 = 345, page = floor(345/50)*50 = 300
    expect(pageStartForIteration(5, 350, PAGE_SIZE)).toBe(300);
  });

  it("returns 0 for the highest iteration number (index 0)", () => {
    // iteration 350 of 350: index = 0, page = 0
    expect(pageStartForIteration(350, 350, PAGE_SIZE)).toBe(0);
  });

  it("returns 0 for an iteration on the first page", () => {
    // iteration 310 of 350: index = 40, page = 0
    expect(pageStartForIteration(310, 350, PAGE_SIZE)).toBe(0);
  });

  it("returns the correct boundary between pages", () => {
    // iteration 301 of 350: index = 49, page = 0
    expect(pageStartForIteration(301, 350, PAGE_SIZE)).toBe(0);
    // iteration 300 of 350: index = 50, page = 50
    expect(pageStartForIteration(300, 350, PAGE_SIZE)).toBe(50);
  });

  it("returns null for iteration 0 (invalid)", () => {
    expect(pageStartForIteration(0, 350, PAGE_SIZE)).toBeNull();
  });

  it("returns null when iterationNum exceeds totalHits", () => {
    expect(pageStartForIteration(400, 350, PAGE_SIZE)).toBeNull();
  });

  it("returns null when totalHits is 0", () => {
    expect(pageStartForIteration(1, 0, PAGE_SIZE)).toBeNull();
  });

  it("handles a loop with exactly one page of iterations", () => {
    // 50 total iterations, asking for iteration 1 (last): index=49, page=0
    expect(pageStartForIteration(1, 50, PAGE_SIZE)).toBe(0);
    // asking for iteration 50 (first): index=0, page=0
    expect(pageStartForIteration(50, 50, PAGE_SIZE)).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// fillIterationPlaceholders
// ---------------------------------------------------------------------------

const TASK_A = { iteration: 350, status: "COMPLETED", taskId: "t350" };
const TASK_B = { iteration: 349, status: "COMPLETED", taskId: "t349" };
const WORKFLOW_TASK = { name: "my_task", taskReferenceName: "my_task_ref" };

describe("fillIterationPlaceholders", () => {
  it("returns loopOver sorted descending when length >= totalIterations", () => {
    const result = fillIterationPlaceholders(
      [TASK_A, TASK_B],
      2,
      "loop_ref",
      WORKFLOW_TASK,
    );
    expect(result).toHaveLength(2);
    expect(result[0]).toMatchObject({ iteration: 350 });
    expect(result[1]).toMatchObject({ iteration: 349 });
  });

  it("fills missing iterations with _summarized placeholders", () => {
    // loopOver has iterations 3 and 2 out of a total of 5; 5, 4, 1 are missing
    const tasks = [
      { iteration: 3, status: "COMPLETED" },
      { iteration: 2, status: "COMPLETED" },
    ];
    const result = fillIterationPlaceholders(
      tasks,
      5,
      "loop_ref",
      WORKFLOW_TASK,
    );
    expect(result).toHaveLength(5);
    const iterations = result.map((r) => r.iteration);
    expect(iterations).toEqual([5, 4, 3, 2, 1]);
    // placeholders for 5, 4, 1
    expect(result.filter((r) => (r as any)._summarized)).toHaveLength(3);
  });

  it("placeholders carry correct metadata", () => {
    const result = fillIterationPlaceholders(
      [TASK_A],
      3,
      "loop_ref",
      WORKFLOW_TASK,
    );
    const placeholder = result.find((r) => (r as any)._summarized);
    expect(placeholder).toBeDefined();
    expect(placeholder).toMatchObject({
      _summarized: true,
      _parentDoWhileRef: "loop_ref",
      _totalIterations: 3,
      workflowTask: WORKFLOW_TASK,
      status: "COMPLETED",
    });
  });

  it("result is sorted descending by iteration number", () => {
    // deliberately out of order input; totalIterations=4, have 3 and 2, missing 4 and 1
    const tasks = [
      { iteration: 2, status: "COMPLETED" },
      { iteration: 3, status: "COMPLETED" },
    ];
    const result = fillIterationPlaceholders(
      tasks,
      4,
      "loop_ref",
      WORKFLOW_TASK,
    );
    const iterations = result.map((r) => r.iteration);
    expect(iterations).toEqual([4, 3, 2, 1]);
    for (let i = 0; i < iterations.length - 1; i++) {
      expect(iterations[i]).toBeGreaterThan(iterations[i + 1]!);
    }
  });

  it("returns empty array when loopOver is empty", () => {
    const result = fillIterationPlaceholders([], 5, "loop_ref", WORKFLOW_TASK);
    expect(result).toHaveLength(0);
  });

  it("does not add duplicates when all iterations are present", () => {
    const tasks = [3, 2, 1].map((n) => ({ iteration: n, status: "COMPLETED" }));
    const result = fillIterationPlaceholders(tasks, 3, "ref", WORKFLOW_TASK);
    const iterations = result.map((r) => r.iteration);
    expect(new Set(iterations).size).toBe(3);
  });

  it("accepts undefined parentDoWhileRef", () => {
    const result = fillIterationPlaceholders(
      [TASK_A],
      2,
      undefined,
      WORKFLOW_TASK,
    );
    const placeholder = result.find((r) => (r as any)._summarized);
    expect(placeholder).toMatchObject({ _parentDoWhileRef: undefined });
  });
});
