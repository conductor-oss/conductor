import { assign, raise } from "xstate";
import _isNaN from "lodash/isNaN";

import {
  CenterOnSelectedTaskEvent,
  HandleZoomEvent,
  PanAndZoomMachineContext,
  ResetZoomPositionEvent,
  SelectNodeEvent,
  SetFitScreenEvent,
  SetInitialViewportOffsetEvent,
  SetLayoutEvent,
  SetPositionEvent,
  SetZoomEvent,
  SetZoomToPositionEvent,
  DragEvent,
  PanAndZoomEventTypes,
  ToggleSearchEvent,
  SetNotifiedEventTypeEvent,
} from "./types";

import { MAX_ZOOM, MIN_ZOOM } from "../constants";
import { ZOOMING_STEP } from "utils/constants/workflow";
import {
  applyZoomToCursor,
  calculateZoomPosition,
  centerInBestLayoutNode,
  initialZoomCenter,
  NodeWithSizeAndPosition,
} from "./helpers";
import { featureFlags, FEATURES } from "utils";

const DRAG_DROP_TASK_INCREMENT_THRESHOLD = featureFlags.getValue(
  FEATURES.DRAG_DROP_TASK_INCREMENT_THRESHOLD,
);

export const resetZoomPosition = assign<
  PanAndZoomMachineContext,
  ResetZoomPositionEvent
>(
  (
    { layout, viewportSize, zoom },
    { viewportOffsetWidth, viewportOffsetHeight },
  ) =>
    initialZoomCenter({
      layout,
      viewportOffsetWidth: viewportOffsetWidth || viewportSize.width,
      viewportOffsetHeight: viewportOffsetHeight || viewportSize.height,
      zoom,
    }),
);

export const setLayout = assign<PanAndZoomMachineContext, SetLayoutEvent>(
  (context, { layout }) => {
    const canvasWidth = layout?.width || context.canvasSize.width;
    const canvasHeight = layout?.height || context.canvasSize.height;
    return {
      canvasSize: { width: canvasWidth, height: canvasHeight },
      layout: layout,
      // viewportSize:{}
    };
  },
);

export const setZoom = assign<PanAndZoomMachineContext, SetZoomEvent>(
  (context, { zoom }) => {
    return calculateZoomPosition({ context, newZoom: zoom });
  },
);

export const setPosition = assign<PanAndZoomMachineContext, SetPositionEvent>({
  position: (__context, { position }) => position,
});

export const setSelectedNode = assign<
  PanAndZoomMachineContext,
  SelectNodeEvent
>({
  selectedNode: (_context, { node }) => node,
});

export const setInitialViewportOffset = assign<
  PanAndZoomMachineContext,
  SetInitialViewportOffsetEvent
>((_, { viewportOffsetWidth, viewportOffsetHeight }) => ({
  lastViewportOffsetWidth: viewportOffsetWidth,
  lastViewportOffsetHeight: viewportOffsetHeight,
  viewportSize: { width: viewportOffsetWidth, height: viewportOffsetHeight },
}));

export const centerUsingContext = assign<PanAndZoomMachineContext>(
  ({
    layout,
    lastViewportOffsetWidth: viewportOffsetWidth,
    lastViewportOffsetHeight: viewportOffsetHeight,
    zoom,
  }) =>
    initialZoomCenter({
      layout,
      viewportOffsetWidth: viewportOffsetWidth!,
      viewportOffsetHeight: viewportOffsetHeight!,
      zoom,
    }),
);

export const centerOnSelectedTask = assign<
  PanAndZoomMachineContext,
  CenterOnSelectedTaskEvent
>((context, { viewportOffsetWidth, viewportOffsetHeight }) => {
  if (context.layout) {
    const { layout, position, selectedNode, zoom } = context;

    const widthToUse = viewportOffsetWidth || context.lastViewportOffsetWidth!;
    const heightToUse =
      viewportOffsetHeight || context.lastViewportOffsetHeight!;

    const newPosition = centerInBestLayoutNode(
      layout?.children || [],
      { width: widthToUse, height: heightToUse },
      zoom,
      selectedNode as NodeWithSizeAndPosition,
    );

    if (newPosition === null) {
      return initialZoomCenter({
        layout,
        viewportOffsetWidth: widthToUse,
        viewportOffsetHeight: heightToUse,
        zoom,
      });
    }

    const { x: positionX, y: positionY } = newPosition || context.position;

    return {
      position: {
        x: _isNaN(positionX) ? (widthToUse - layout!.width!) / 2 : positionX,
        y: _isNaN(positionY) ? position.y : positionY,
      },
      lastViewportOffsetWidth: widthToUse,
      lastViewportOffsetHeight: heightToUse,
      viewportSize: { width: widthToUse, height: heightToUse },
    };
  }

  return context;
});

export const fitToScreen = assign<PanAndZoomMachineContext, SetFitScreenEvent>(
  (context, { viewportOffsetWidth, viewportOffsetHeight }) => {
    const { layout } = context;

    // Calculate the scale ratio for both width and height
    const widthRatio = layout?.width ? viewportOffsetWidth / layout.width : 1;
    const heightRatio = layout?.height
      ? viewportOffsetHeight / layout.height
      : 1;
    // Use the smaller ratio to fit the canvas into the viewport
    const scale = Math.min(widthRatio, heightRatio);

    // Calculate the new diagram width and height
    const newDiagramWidth = (layout?.width || 1) * scale;
    const newDiagramHeight = (layout?.height || 1) * scale;

    // Calculate the position of the diagram in the viewport
    const positionX = Math.ceil(
      widthRatio === scale ? 0 : (viewportOffsetWidth - newDiagramWidth) / 2,
    );
    const positionY = Math.ceil((viewportOffsetHeight - newDiagramHeight) / 2);

    return {
      position: {
        x: positionX,
        y: positionY,
      },
      zoom: scale,
    };
  },
);

export const setZoomToPosition = assign<
  PanAndZoomMachineContext,
  SetZoomToPositionEvent
>((context, { zoom, position }) => {
  const currentPosition = context.position;
  const oldZoom = context.zoom;

  return applyZoomToCursor(currentPosition, position, oldZoom, zoom);
});

export const handleZoom = assign<PanAndZoomMachineContext, HandleZoomEvent>(
  (context, { isZoomOut }) => {
    const roundedContextZoom = Math.round(context.zoom * 10) / 10;
    const newZoom = isZoomOut
      ? roundedContextZoom - ZOOMING_STEP
      : roundedContextZoom + ZOOMING_STEP;

    if (isZoomOut && newZoom > MIN_ZOOM) {
      return calculateZoomPosition({ context, newZoom });
    }
    if (!isZoomOut && newZoom <= MAX_ZOOM) {
      return calculateZoomPosition({ context, newZoom });
    }

    return context;
  },
);

const INCREMENT_THRESHOLD = isNaN(DRAG_DROP_TASK_INCREMENT_THRESHOLD)
  ? 10
  : Number(DRAG_DROP_TASK_INCREMENT_THRESHOLD);

const MIN_ALLOWED_WIDTH = 210; // Estimated from the menu bar to the left
const MIN_ALLOWED_HEIGHT = 180; // Estimated from the menu bar to the top
export const setPositionOfDraggingTask = assign<
  PanAndZoomMachineContext,
  DragEvent
>((context, { clientMousePosition }) => {
  let draggingUpdatedPosition = context.draggingUpdatedPosition;
  const maxAllowedWidth = context.lastViewportOffsetWidth! - 10;

  const maxAllowedHeight = context.lastViewportOffsetHeight! - 100;

  const currentPosition = { ...context.position };

  if (clientMousePosition.x >= maxAllowedWidth) {
    currentPosition.x = context.position.x - INCREMENT_THRESHOLD;
    draggingUpdatedPosition = true;
  }

  if (clientMousePosition.x <= MIN_ALLOWED_WIDTH) {
    currentPosition.x = context.position.x + INCREMENT_THRESHOLD;

    draggingUpdatedPosition = true;
  }

  if (clientMousePosition.y >= maxAllowedHeight) {
    currentPosition.y = context.position.y - INCREMENT_THRESHOLD;

    draggingUpdatedPosition = true;
  }

  if (clientMousePosition.y <= MIN_ALLOWED_HEIGHT) {
    // Note this represents the top of the screen
    currentPosition.y = context.position.y + INCREMENT_THRESHOLD;

    draggingUpdatedPosition = true;
  }
  return {
    position: currentPosition,
    draggingUpdatedPosition,
  };
});
export const cleanUpPositionUpdatedFlag = assign<PanAndZoomMachineContext>({
  draggingUpdatedPosition: false,
});

export const fireToggleSearchField = raise<
  PanAndZoomMachineContext,
  ToggleSearchEvent
>(
  {
    type: PanAndZoomEventTypes.TOGGLE_SEARCH_EVT,
  },
  { delay: 200 },
);

export const setNotifiedEventType = assign<
  PanAndZoomMachineContext,
  SetNotifiedEventTypeEvent
>((__, event) => {
  return { notifiedEventType: event.eventType };
});
