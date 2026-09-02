/**
 * Pure helper functions for DO_WHILE iteration UI logic.
 *
 * All functions here are free of React and side-effects so they can be
 * imported and tested directly without a component harness.
 */
/**
 * Given a numeric string prefix typed by the user and the total iteration
 * count, returns up to 6 iteration numbers that start with that prefix.
 *
 * Strategy: exact match first, then prefix-scaled by powers of 10.
 * e.g. prefix="5", max=350 → [5, 50, 51, 52, 53, 54]
 * e.g. prefix="35", max=350 → [35, 350]
 */
export declare function buildSuggestions(prefix: string, max: number): number[];
/**
 * Calculates the `start` offset (zero-based index into the server's
 * descending iteration list) for the page that contains `iterationNum`.
 *
 * The server returns iterations newest-first, so iteration N is at index
 * (totalHits - N) in the full list.
 *
 * Returns `null` if the iteration is out of range.
 */
export declare function pageStartForIteration(iterationNum: number, totalHits: number, pageSize: number): number | null;
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
export declare function fillIterationPlaceholders<T extends {
    iteration?: number;
}>(loopOver: T[], totalIterations: number, parentDoWhileRef: string | undefined, workflowTask: unknown): (T | IterationPlaceholder)[];
