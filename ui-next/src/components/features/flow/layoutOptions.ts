import { NodeData } from "reaflow";
import { NodeTaskData } from "components/features/flow/nodes/mapper";
import { EDGE_SPACING } from "./components/graphs/PanAndZoomWrapper/constants";
import { sideTaskLaneWidth } from "./components/shapes/sideTaskLane";

/** Room around the graph, in the units ELK lays out in. */
export const GRAPH_PADDING = { top: 10, left: 100, bottom: 10, right: 100 };

/**
 * The ELK options the canvas is laid out with.
 *
 * The only thing here that depends on the graph is the right padding. Side tasks are drawn past a
 * node's right edge, and reaflow sizes its `<svg>` to the graph ELK measured, so a card that
 * reaches beyond the padding is clipped by the canvas — the node's `overflow: visible` only gets it
 * out of the node. Widening the padding leaves every node and edge exactly where it was and simply
 * gives the canvas room; a graph with no side tasks is laid out unchanged.
 */
export const flowLayoutOptions = (nodes: NodeData<NodeTaskData>[] = []) => ({
  "org.eclipse.elk.spacing.edgeEdge": EDGE_SPACING.toString(),
  "org.eclipse.elk.padding": `[top=${GRAPH_PADDING.top},left=${GRAPH_PADDING.left},bottom=${GRAPH_PADDING.bottom},right=${GRAPH_PADDING.right + sideTaskLaneWidth(nodes)}]`,
  "org.eclipse.elk.layered.edgeLabels.centerLabelPlacementStrategy":
    "SPACE_EFFICIENT_LAYER",
  "org.eclipse.elk.nodeLabels.placement": "V_CENTER",
});
