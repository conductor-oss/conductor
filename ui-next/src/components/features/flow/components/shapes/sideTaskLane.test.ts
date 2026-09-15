import { describe, expect, it } from "vitest";
import {
  SIDE_TASK_CARD_WIDTH,
  SIDE_TASK_GUTTER,
  SIDE_TASK_LANE_WIDTH,
  sideTaskLaneWidth,
} from "./sideTaskLane";

/**
 * The bug this pins: the cards were drawn 204px past a node's right edge into a graph whose right
 * padding was 100px, and reaflow sizes its `<svg>` to that graph, so they were cut in half. The
 * reserved lane has to cover everything the renderer can draw — the widest card, the gutter before
 * it, and the offset a hovered stack fans out to.
 */
describe("the side task lane", () => {
  const node = (sideTasks?: unknown[]) =>
    ({ id: "n", data: sideTasks ? { sideTasks } : {} }) as any;

  it("reserves nothing for a graph with no side tasks", () => {
    expect(sideTaskLaneWidth([node(), node()])).toBe(0);
    expect(sideTaskLaneWidth([])).toBe(0);
    expect(sideTaskLaneWidth(undefined)).toBe(0);
  });

  it("reserves nothing when a node carries an empty list", () => {
    expect(sideTaskLaneWidth([node([])])).toBe(0);
  });

  it("reserves the lane as soon as any one node has side tasks", () => {
    expect(sideTaskLaneWidth([node(), node([{}])])).toBe(SIDE_TASK_LANE_WIDTH);
  });

  it("reserves more than the cards can ever occupy", () => {
    expect(SIDE_TASK_LANE_WIDTH).toBeGreaterThan(
      SIDE_TASK_GUTTER + SIDE_TASK_CARD_WIDTH,
    );
  });
});
