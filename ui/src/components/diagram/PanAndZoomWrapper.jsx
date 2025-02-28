import { useDrag, usePinch, useWheel } from "@use-gesture/react";
import { useCallback, useEffect, useRef, useState } from "react";
import domToImage from "dom-to-image";
import { ZoomControls } from "./ZoomControls";

export const MIN_ZOOM = 0.02;
export const MAX_ZOOM = 2;
export const ZOOMING_STEP = 0.1;

export const applyZoomToCursor = (
  currentPosition,
  cursorPosition,
  oldZoom,
  newZoom
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

const Viewport = ({ viewportRef, children }) => {
  const backgroundStyle = {
    backgroundColor: "#FFFFFF",
    backgroundImage: `url('/diagramDotBg.svg')`,
  };

  return (
    <div
      id="viewport-container"
      data-cy="pan-and-zoom-wrapper-viewport"
      style={{
        width: "100%",
        height: "100%",
        position: "relative",
        overflow: "hidden",
        transition: "opacity .2s",
        touchAction: "none",
        ...backgroundStyle,
      }}
      ref={viewportRef}
    >
      {children}
    </div>
  );
};

function PanAndZoomWrapper({ children, layout, workflowName }) {
  const viewportRef = useRef(null);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(0.75);

  const handleSetPosition = useCallback((val) => {
    setPosition(val);
  }, []);
  const getRelativeCursorPosition = useCallback((event) => {
    // Get current cursor position with the viewportRef
    const rect = viewportRef?.current?.getBoundingClientRect();

    return {
      x: event.clientX - (rect?.left ?? 0),
      y: event.clientY - (rect?.top ?? 0),
    };
  }, []);

  const handleSetZoomAndPosition = useCallback(
    (newPosition, newZoom) => {
      const currentPosition = position;
      const oldZoom = zoom;

      const data = applyZoomToCursor(
        currentPosition,
        newPosition,
        oldZoom,
        newZoom
      );
      handleSetPosition({ x: data.position.x, y: data.position.y });
      setZoom(data.zoom);
    },
    [handleSetPosition, position, zoom]
  );

  const scrollCallback = useCallback(
    ({ delta, event, metaKey, ctrlKey, direction }) => {
      event.stopPropagation();
      event.preventDefault();

      if ((metaKey || ctrlKey) && direction[1] !== 0) {
        const zoomSensitivity = 0.003; // Adjust this value to control zoom sensitivity
        let newZoom = zoom * (1 - event.deltaY * zoomSensitivity);

        if (newZoom < MIN_ZOOM) {
          newZoom = MIN_ZOOM;
        } else if (newZoom > MAX_ZOOM) {
          newZoom = MAX_ZOOM;
        }

        const cursorPosition = getRelativeCursorPosition(event);

        handleSetZoomAndPosition(
          { x: cursorPosition.x, y: cursorPosition.y },
          newZoom
        );
      } else {
        const newX = position.x - delta[0];
        const newY = position.y - delta[1];
        handleSetPosition({ x: newX, y: newY });
      }
    },
    [
      getRelativeCursorPosition,
      handleSetZoomAndPosition,
      handleSetPosition,
      zoom,
      position,
    ]
  );

  useWheel(scrollCallback, {
    enabled: !!layout,
    target: viewportRef?.current,
    eventOptions: { passive: false },
  });

  const isEventReallyWheel = (event) => {
    return Math.abs(event.deltaY) > 25;
  };

  usePinch(
    ({ offset: [factor], event }) => {
      event.stopPropagation();
      // This event needs to send the position of the mouse in the viewport. to handle zoom there
      // and should disable scroll events for a period of time.
      if (!isEventReallyWheel(event)) {
        const cursorPosition = getRelativeCursorPosition(event);

        handleSetZoomAndPosition(
          { x: cursorPosition.x, y: cursorPosition.y },
          factor
        );
      }
    },
    {
      scaleBounds: { min: MIN_ZOOM, max: MAX_ZOOM },
      from: zoom,
      enabled: !!layout,
      target: viewportRef?.current,
      eventOptions: { passive: false },
    }
  );

  useDrag(
    (props) => {
      const { delta, event, tap } = props;
      event.stopPropagation();
      const newX = position.x + delta[0];
      const newY = position.y + delta[1];

      // Filter to prevent onClick event
      if (!tap) {
        handleSetPosition({ x: newX, y: newY });
      }
    },
    {
      target: viewportRef?.current,
      eventOptions: { passive: false },
      filterTaps: true,
    }
  );

  // const handleZoom = (val) => {
  //   setZoom(val);
  // };

  const initialZoomCenter = useCallback(
    ({ layout, viewportOffsetWidth, viewportOffsetHeight, zoom }) => {
      const [startNode] = layout?.children;

      const centerPosition = centerCanvasToNodePosition(
        {
          width: viewportOffsetWidth,
          height: viewportOffsetHeight,
        },
        startNode,
        zoom
      );

      return {
        position: {
          x: centerPosition?.x,
          // Padding top & control bar height (40)
          y: startNode?.y + 65,
        },
        zoom,
        viewportSize: {
          width: viewportOffsetWidth,
          height: viewportOffsetHeight,
        },
        lastViewportOffsetWidth: viewportOffsetWidth,
        lastViewportOffsetHeight: viewportOffsetHeight,
      };
    },
    []
  );

  const handleResetZoomPosition = useCallback(
    (viewportOffsetWidth, viewportOffsetHeight) => {
      const result = initialZoomCenter({
        layout,
        viewportOffsetWidth: viewportOffsetWidth,
        viewportOffsetHeight: viewportOffsetHeight,
        zoom,
      });

      handleSetPosition(result.position);
      setZoom(result.zoom);
    },
    [handleSetPosition, initialZoomCenter, layout, zoom]
  );

  const centerCanvasToNodePosition = (containerSize, node, scale) => {
    // Calculate position of the canvas to center at X coordinate
    const viewPortCenterX = containerSize?.width / 2;
    const realXPosition = node?.width / 2 + node?.x; // X coordinate of the node plus half of the node width
    const scaledXCoordinate = realXPosition * scale; // Scale X coordinate
    const positionX = viewPortCenterX - scaledXCoordinate; // Center of the viewport minus the scaled X coordinate

    const viewportCenterY = containerSize?.height / 2;
    const realYPosition = node?.height / 2 + node?.y; // Y coordinate of the node plus half of the node height
    const scaledYCoordinate = realYPosition * scale; // Scale Y coordinate
    const positionY = viewportCenterY - scaledYCoordinate; // Center of the viewport minus the scaled Y coordinate

    return {
      x: positionX,
      y: positionY,
    };
  };

  const resetPosition = useCallback(() => {
    if (viewportRef?.current) {
      const { offsetWidth, offsetHeight } = viewportRef.current;

      handleResetZoomPosition(offsetWidth, offsetHeight);
    }
  }, [viewportRef, handleResetZoomPosition]);

  const handleSetFitScreen = useCallback(
    (viewportOffsetWidth, viewportOffsetHeight) => {
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
        widthRatio === scale ? 0 : (viewportOffsetWidth - newDiagramWidth) / 2
      );
      const positionY = Math.ceil(
        (viewportOffsetHeight - newDiagramHeight) / 2
      );
      handleSetPosition({
        x: positionX,
        y: positionY,
      });
      setZoom(scale);
    },
    [handleSetPosition, layout]
  );

  const fitToScreen = useCallback(() => {
    if (viewportRef?.current) {
      const { offsetWidth, offsetHeight } = viewportRef.current;

      handleSetFitScreen(offsetWidth, offsetHeight);
    }
  }, [viewportRef, handleSetFitScreen]);

  const calculateZoomPosition = ({ newZoom }) => {
    const currentPosition = position;
    const oldZoom = zoom;

    // Strategy:
    // Try to keep the position Y that will make the diagram zoom in/out center of X

    // Old center position (C0)
    const oldCenterX = (layout?.width * oldZoom) / 2;
    // const oldCenterY = (layout?.height * oldZoom) / 2;

    // New center position (C1)
    const newCenterX = (layout?.width * newZoom) / 2;
    // const newCenterY = (layout?.height * newZoom) / 2;

    // Delta
    const deltaX = oldCenterX - newCenterX;
    // const deltaY = oldCenterY - newCenterY;

    setZoom(newZoom);
    handleSetPosition({
      x: currentPosition.x + deltaX,
      y: currentPosition.y,
    });
  };

  const handleZoom = (isZoomOut) => {
    const roundedContextZoom = Math.round(zoom * 10) / 10;
    const newZoom = isZoomOut
      ? roundedContextZoom - ZOOMING_STEP
      : roundedContextZoom + ZOOMING_STEP;

    if (isZoomOut && newZoom > MIN_ZOOM) {
      calculateZoomPosition({ newZoom });
    }
    if (!isZoomOut && newZoom <= MAX_ZOOM) {
      calculateZoomPosition({ newZoom });
    }
  };

  useEffect(() => {
    if (viewportRef?.current && layout?.children) {
      const { offsetWidth, offsetHeight } = viewportRef.current;
      handleResetZoomPosition(offsetWidth, offsetHeight);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [layout, viewportRef]);

  const printScreen = (workflowName) => {
    const node = document.getElementById("diagram-canvas-container");

    if (!node?.firstChild) return;

    domToImage
      .toPng(node.firstChild)
      .then(function (dataUrl) {
        const link = document.createElement("a");
        link.download = `${workflowName}.png`;
        link.href = dataUrl;
        link.click();
      })
      .catch(function (error) {
        console.error("Error saving image:", error);
      });
  };

  return (
    <Viewport viewportRef={viewportRef}>
      <ZoomControls
        zoom={zoom}
        setZoom={handleZoom}
        layout={layout}
        resetPosition={resetPosition}
        fitToScreen={fitToScreen}
        printScreen={() => printScreen(workflowName || "workflow_diagram")}
      />
      <div id="workflow-diagram-outer">
        <div
          id="pan-and-zoom-wrapper-diagram-container"
          style={{
            position: "relative",
            transformOrigin: "top left",
            transition: "transform .1s",
            transform: `translateX(${position.x}px) translateY(${position.y}px) scale(${zoom})`,
            width: layout?.width, // this is the same size as the layout. only initialized
            height: layout?.height,
          }}
        >
          <div
            id="diagram-canvas-container"
            style={{
              position: "absolute",
              width: layout?.width,
              height: layout?.height,
            }}
          >
            {children}
          </div>
        </div>
      </div>
    </Viewport>
  );
}

export default PanAndZoomWrapper;
