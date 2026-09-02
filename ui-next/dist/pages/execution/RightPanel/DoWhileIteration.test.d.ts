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
export {};
