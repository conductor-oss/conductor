import { NodeData } from "reaflow";
import { NodeTaskData } from "components/features/flow/nodes/mapper";

/**
 * The strip of canvas to the right of a node that its side tasks are drawn in.
 *
 * These sizes are shared by the renderer and the layout on purpose. A side task escapes its node
 * through the `<foreignObject overflow: visible>` it lives in, but nothing lets it escape the
 * canvas: reaflow sizes its `<svg>` to the graph ELK measured, and an SVG root clips its own
 * viewport. So the graph has to be laid out with room for the lane, or the cards are cut off at
 * whatever the graph padding happens to be.
 */

export const SIDE_TASK_CARD_WIDTH = 220;
export const SIDE_TASK_CARD_HEIGHT = 34;
export const SIDE_TASK_CARD_GAP = 6;
export const SIDE_TASK_STACK_OFFSET = 5;
export const SIDE_TASK_MAX_STACK_CARDS = 3;
export const SIDE_TASK_GUTTER = 14;

/** Hover fans the stack out to twice its resting offset, which is its widest state. */
const STACK_SPREAD =
  (SIDE_TASK_MAX_STACK_CARDS - 1) * SIDE_TASK_STACK_OFFSET * 2;

/** How far past a node's right edge its side tasks can reach. */
export const SIDE_TASK_LANE_WIDTH =
  SIDE_TASK_GUTTER + SIDE_TASK_CARD_WIDTH + STACK_SPREAD;

/**
 * The lane this graph needs, which is nothing at all unless some node actually has side tasks —
 * every other diagram, execution or definition, lays out exactly as it did before.
 */
export const sideTaskLaneWidth = (nodes: NodeData<NodeTaskData>[] = []) =>
  nodes.some((node) => (node?.data?.sideTasks?.length ?? 0) > 0)
    ? SIDE_TASK_LANE_WIDTH
    : 0;
