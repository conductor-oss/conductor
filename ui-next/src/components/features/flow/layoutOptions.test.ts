import { describe, expect, it } from "vitest";
import { elkLayout } from "reaflow";
import { flowLayoutOptions, GRAPH_PADDING } from "./layoutOptions";
import { SIDE_TASK_LANE_WIDTH } from "./components/shapes/sideTaskLane";

/**
 * This runs the real ELK engine, because the bug was arithmetic the diagram only revealed once it
 * was on screen: the cards were drawn 204px past a node's right edge into a graph whose right
 * padding was 100px. reaflow sizes its `<svg>` to the graph ELK returns and an SVG root clips its
 * own viewport, so the far half of every card was cut off.
 *
 * What has to hold is that the graph is wide enough for the lane to be drawn in.
 */
describe("laying out a graph that has side tasks", () => {
  const NODE_WIDTH = 350;

  const graph = (withSideTasks: boolean) => {
    const nodes = [
      {
        id: "llm",
        width: NODE_WIDTH,
        height: 100,
        data: withSideTasks ? { sideTasks: [{ taskId: "t1" }] } : {},
      },
      { id: "notify", width: NODE_WIDTH, height: 100, data: {} },
    ] as any[];
    return { nodes, edges: [{ id: "e1", from: "llm", to: "notify" }] };
  };

  const layoutOf = async (withSideTasks: boolean) => {
    const { nodes, edges } = graph(withSideTasks);
    const layout: any = await elkLayout(
      nodes as any,
      edges as any,
      flowLayoutOptions(nodes as any) as any,
    );
    const node = layout.children.find((child: any) => child.id === "llm");
    return { layout, node, rightEdge: node.x + node.width };
  };

  it("leaves the graph alone when no node has side tasks", async () => {
    const { layout, rightEdge } = await layoutOf(false);

    expect(layout.width).toBe(rightEdge + GRAPH_PADDING.right);
  });

  it("makes room for the whole lane when a node has them", async () => {
    const { layout, rightEdge } = await layoutOf(true);

    expect(layout.width - rightEdge).toBeGreaterThanOrEqual(
      SIDE_TASK_LANE_WIDTH,
    );
  });

  it("would have clipped the cards on the padding alone", async () => {
    // The regression, as the number it actually was: 100px of room for 234px of card.
    const { layout, rightEdge } = await layoutOf(false);

    expect(layout.width - rightEdge).toBeLessThan(SIDE_TASK_LANE_WIDTH);
  });

  it("does not move a single node, only the canvas around it", async () => {
    const without = await layoutOf(false);
    const with_ = await layoutOf(true);

    expect(with_.node.x).toBe(without.node.x);
    expect(with_.node.y).toBe(without.node.y);
    expect(with_.layout.height).toBe(without.layout.height);
  });
});
