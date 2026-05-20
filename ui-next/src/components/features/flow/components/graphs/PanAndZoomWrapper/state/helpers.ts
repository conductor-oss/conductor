import { ElkRoot, NodeData } from "reaflow";
import { PanAndZoomMachineContext, PositionProps, SizeProps } from "./types";

type CenterParams = {
  layout?: ElkRoot;
  viewportOffsetWidth: number;
  viewportOffsetHeight: number;
  zoom: number;
};

type SizeAndPosition = PositionProps & { width: number; height: number };

export const PADDING_TOP = 65;

export const centerCanvasToNodePosition = (
  containerSize: SizeProps,
  node: SizeAndPosition,
  scale: number,
) => {
  // Calculate position of the canvas to center at X coordinate
  const viewPortCenterX = containerSize.width / 2;
  const realXPosition = node.width / 2 + node.x; // X coordinate of the node plus half of the node width
  const scaledXCoordinate = realXPosition * scale; // Scale X coordinate
  const positionX = viewPortCenterX - scaledXCoordinate; // Center of the viewport minus the scaled X coordinate

  const viewportCenterY = containerSize.height / 2;
  const realYPosition = node.height / 2 + node.y; // Y coordinate of the node plus half of the node height
  const scaledYCoordinate = realYPosition * scale; // Scale Y coordinate
  const positionY = viewportCenterY - scaledYCoordinate; // Center of the viewport minus the scaled Y coordinate

  return {
    x: positionX,
    y: positionY,
  };
};

export type NodeWithSizeAndPosition = NodeData &
  SizeAndPosition & { children?: NodeWithSizeAndPosition[] };

export const centerInBestLayoutNode = (
  children: NodeWithSizeAndPosition[],
  containerSize: SizeProps,
  scale: number,
  selectedNode?: NodeWithSizeAndPosition,
): SizeAndPosition | undefined => {
  // No children. then nothing to do.
  if (children.length === 0 || selectedNode == null) return undefined;

  // If no selected node center somewhere
  const nodeSelected = selectedNode; //|| _first(children)!;

  for (const node of children) {
    if (node.id === nodeSelected.id) {
      return {
        ...centerCanvasToNodePosition(containerSize, node, scale), // Node found cool center according to parameters
        width: node.width,
        height: node.height,
      };
    }
    // Node not was not found but has children look for childs
    if (node.children) {
      const result = centerInBestLayoutNode(
        // the node has to be centered relative to its container so in this case the paren is the container
        node.children,
        node,
        1,
        selectedNode,
      );
      if (result) {
        // result was found
        const resultPosition = centerCanvasToNodePosition(
          // Center using our real container size
          containerSize,
          {
            x: node.x - result.x, // we move inside our new container according to the result of the previous center
            y: node.y - result.y,
            width: node.width,
            height: node.height,
          },
          scale,
        );
        return {
          ...resultPosition,
          width: node.width,
          height: node.height,
        };
      }
    }
  }
};

export const initialZoomCenter = ({
  layout,
  viewportOffsetWidth,
  viewportOffsetHeight,
  zoom,
}: CenterParams): Partial<PanAndZoomMachineContext> => {
  const startNode = layout?.children?.[0];

  if (!startNode) {
    return {};
  }

  const centerPosition = centerCanvasToNodePosition(
    {
      width: viewportOffsetWidth,
      height: viewportOffsetHeight,
    },
    startNode,
    zoom,
  );

  return {
    position: {
      x: centerPosition.x,
      // Padding top & control bar height (40)
      y: startNode.y + PADDING_TOP,
    },
    zoom,
    viewportSize: { width: viewportOffsetWidth, height: viewportOffsetHeight },
    lastViewportOffsetWidth: viewportOffsetWidth,
    lastViewportOffsetHeight: viewportOffsetHeight,
  };
};

export const applyZoomToCursor = (
  currentPosition: { x: number; y: number },
  cursorPosition: { x: number; y: number },
  oldZoom: number,
  newZoom: number,
) => {
  // Calculate the change in zoom
  const zoomFactor = newZoom / oldZoom;

  // Calculate the new position to keep the cursor in the same position relative to the canvas content
  const deltaX = (cursorPosition.x - currentPosition.x) * (1 - zoomFactor);
  const deltaY = (cursorPosition.y - currentPosition.y) * (1 - zoomFactor);

  return {
    position: {
      x: currentPosition.x + deltaX,
      y: currentPosition.y + deltaY,
    },
    zoom: newZoom,
  };
};

export const calculateZoomPosition = ({
  context,
  newZoom,
}: {
  context: PanAndZoomMachineContext;
  newZoom: number;
}) => {
  const { layout, position: currentPosition, zoom: oldZoom } = context;

  // Strategy:
  // Try to keep the position Y that will make the diagram zoom in/out center of X

  // Old center position (C0)
  const oldCenterX = (layout?.width ?? 0 * oldZoom) / 2;
  // const oldCenterY = (layout?.height! * oldZoom) / 2;

  // New center position (C1)
  const newCenterX = (layout?.width ?? 0 * newZoom) / 2;
  // const newCenterY = (layout?.height! * newZoom) / 2;

  // Delta
  const deltaX = oldCenterX - newCenterX;
  // const deltaY = oldCenterY - newCenterY;

  return {
    zoom: newZoom,
    position: {
      x: currentPosition.x + deltaX,
      // if you need to shrink/expand to the center => + deltaY
      y: currentPosition.y,
    },
  };
};
