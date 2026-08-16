/**
 * Pure helper functions for DO_WHILE iteration UI logic.
 *
 * All functions here are free of React and side-effects so they can be
 * imported and tested directly without a component harness.
 */

// ---------------------------------------------------------------------------
// Jump-to autocomplete suggestions
// ---------------------------------------------------------------------------

/**
 * Given a numeric string prefix typed by the user and the total iteration
 * count, returns up to 6 iteration numbers that start with that prefix.
 *
 * Strategy: exact match first, then prefix-scaled by powers of 10.
 * e.g. prefix="5", max=350 → [5, 50, 51, 52, 53, 54]
 * e.g. prefix="35", max=350 → [35, 350]
 */
export function buildSuggestions(prefix: string, max: number): number[] {
  if (!prefix || !/^\d+$/.test(prefix)) return [];
  const base = parseInt(prefix, 10);
  if (base < 1 || base > max) return [];

  const results: number[] = [];
  if (base <= max) results.push(base);

  let mult = 10;
  while (results.length < 6) {
    const start = base * mult;
    if (start > max) break;
    const end = Math.min((base + 1) * mult - 1, max);
    for (let i = start; i <= end && results.length < 6; i++) results.push(i);
    mult *= 10;
  }
  return results;
}

// ---------------------------------------------------------------------------
// Page calculation for random-access fetches
// ---------------------------------------------------------------------------

/**
 * Calculates the `start` offset (zero-based index into the server's
 * descending iteration list) for the page that contains `iterationNum`.
 *
 * The server returns iterations newest-first, so iteration N is at index
 * (totalHits - N) in the full list.
 *
 * Returns `null` if the iteration is out of range.
 */
export function pageStartForIteration(
  iterationNum: number,
  totalHits: number,
  pageSize: number,
): number | null {
  if (iterationNum < 1 || iterationNum > totalHits || totalHits === 0)
    return null;
  const index = totalHits - iterationNum;
  return Math.floor(index / pageSize) * pageSize;
}

// ---------------------------------------------------------------------------
// Placeholder filling for pruned iterations
// ---------------------------------------------------------------------------

export interface IterationPlaceholder {
  iteration: number;
  status: string;
  _summarized: true;
  _parentDoWhileRef: string | undefined;
  _totalIterations: number;
  workflowTask: unknown;
}

/**
 * Given the task objects the server returned for an inner DO_WHILE task
 * (`loopOver`, newest-first) and the authoritative total iteration count from
 * the parent DO_WHILE task, returns a full descending list of tasks by filling
 * missing iterations with lightweight `_summarized: true` placeholders.
 *
 * When the server has pruned older iteration records (keepLastN / large loops),
 * only the most recent N tasks appear in `loopOver`.  This function restores
 * the full count so the UI can show the complete iteration history.
 */
export function fillIterationPlaceholders<T extends { iteration?: number }>(
  loopOver: T[],
  totalIterations: number,
  parentDoWhileRef: string | undefined,
  workflowTask: unknown,
): (T | IterationPlaceholder)[] {
  if (!loopOver.length || loopOver.length >= totalIterations) {
    return [...loopOver].sort(
      (a, b) => (b.iteration ?? 0) - (a.iteration ?? 0),
    );
  }

  const existingNums = new Set(loopOver.map((t) => t.iteration ?? 0));
  const result: (T | IterationPlaceholder)[] = [...loopOver];

  for (let i = totalIterations; i >= 1; i--) {
    if (!existingNums.has(i)) {
      result.push({
        iteration: i,
        status: "COMPLETED",
        _summarized: true,
        _parentDoWhileRef: parentDoWhileRef,
        _totalIterations: totalIterations,
        workflowTask,
      });
    }
  }

  result.sort((a, b) => (b.iteration ?? 0) - (a.iteration ?? 0));
  return result;
}
