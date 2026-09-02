/**
 * Tests for IterationSection's WORKFLOW_SUMMARIZE feature-flag gate.
 *
 * `summarizeEnabled` is a module-level constant evaluated at import time.
 * To test both flag values without expensive per-test module resets, each
 * describe block calls vi.resetModules() + vi.doMock() once in beforeAll and
 * imports IterationSection a single time. Tests in the block reuse that import.
 *
 * InlineTaskIterations and DoWhileIteration are replaced with lightweight stubs
 * that render the real SummarizeToggle when they receive an onToggleSummarize
 * callback — the same conditional the real components implement. This keeps
 * the module tree small (fast) while still asserting on actual rendered UI.
 * Dedicated component tests for each child component belong in their own files.
 *
 * Only useFullWorkflowQuery is mocked to avoid requiring a QueryClientProvider.
 */
export {};
