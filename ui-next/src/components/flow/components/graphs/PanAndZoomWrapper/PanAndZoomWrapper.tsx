import { Box } from "@mui/material";
import { Handler } from "@use-gesture/core/types";
import { useDrag, usePinch, useWheel } from "@use-gesture/react";
import { useSelector } from "@xstate/react";
import { FlowEvents } from "components/flow/state";
import { selectWorkflowName } from "components/flow/state/selectors";
import domToImage from "dom-to-image";
import {
  FunctionComponent,
  ReactNode,
  Ref,
  useCallback,
  useContext,
  useEffect,
  useRef,
  WheelEvent,
} from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { ActorRef } from "xstate";
import { MAX_ZOOM, MIN_ZOOM } from "./constants";
import PanAndZoomContextProvider from "./PanAndZoomProvider";
import { PanAndZoomEvents, usePanAndZoomActor } from "./state";
import { ZoomControls } from "./ZoomControls";

const isEventReallyWheel = (event: WheelEvent) => {
  return Math.abs(event.deltaY) > 25;
};

const printScreen = (workflowName: string) => {
  const node = document.getElementById("diagram-canvas-container");

  if (!node?.firstChild) return;

  domToImage
    .toPng(node.firstChild)
    .then(function (dataUrl: string) {
      const link = document.createElement("a");
      link.download = `${workflowName}.png`;
      link.href = dataUrl;
      link.click();
    })
    .catch(function (error: Error) {
      console.error("Error saving image:", error);
    });
};

interface ViewportProps {
  viewportRef: Ref<unknown>;
  cursor: string;
  isInconsistent: boolean;
  children: ReactNode;
}

const Viewport: FunctionComponent<ViewportProps> = ({
  viewportRef,
  cursor,
  isInconsistent,
  children,
}) => {
  const { mode } = useContext(ColorModeContext);
  const darkMode = mode === "dark";
  const backgroundStyle = {
    backgroundColor: darkMode ? "#000000" : "#FFFFFF",
    backgroundImage: `url('/diagramDotBg.svg')`,
  };

  return (
    <Box
      id="viewport-container"
      data-cy="pan-and-zoom-wrapper-viewport"
      style={{
        width: "100%",
        height: "100%",
        position: "relative",
        cursor: cursor,
        overflow: "hidden",
        transition: "opacity .2s",
        touchAction: "none",
        ...(isInconsistent ? { opacity: ".5" } : {}),
        ...backgroundStyle,
      }}
      ref={viewportRef}
    >
      {children}
    </Box>
  );
};
interface PanAndZoomWrapperProps {
  isInconsistent: boolean;
  panAndZoomActor: ActorRef<PanAndZoomEvents>;
  leftPanelExpanded: boolean; // TODO this has to be in xstate.
  viewPortChildren?: ReactNode;
  children: ReactNode;
  flowActor: ActorRef<FlowEvents>;
  isExecutionView?: boolean;
}

const PanAndZoomWrapper: FunctionComponent<PanAndZoomWrapperProps> = ({
  isInconsistent,
  panAndZoomActor,
  children,
  leftPanelExpanded, // TODO this has to be in xstate.
  viewPortChildren = null,
  flowActor,
  isExecutionView = false,
}) => {
  const [
    { zoom, canvasSize, layout, position, panEnabled, isSearchFieldVisible },
    {
      handleResetZoomPosition,
      handleSetPosition,
      handleCenterOnSelectedTask,
      handleSetInitialViewportOffset,
      handleSetFitScreen,
      handleZoom,
      handleDrag,
      handleTogglePan,
      handleToggleSearchField,
      handleSetZoomAndPosition,
    },
  ] = usePanAndZoomActor(panAndZoomActor);

  const workflowName = useSelector(flowActor, selectWorkflowName);

  const viewportRef = useRef<HTMLDivElement | null>(null);

  const getRelativeCursorPosition = useCallback((event: WheelEvent) => {
    // Get current cursor position with the viewportRef
    const rect = viewportRef?.current?.getBoundingClientRect();

    return {
      x: event.clientX - (rect?.left ?? 0),
      y: event.clientY - (rect?.top ?? 0),
    };
  }, []);

  const resetPosition = useCallback(() => {
    if (canvasSize.height > 0 && viewportRef?.current) {
      const { offsetWidth, offsetHeight } = viewportRef.current;

      handleResetZoomPosition(offsetWidth, offsetHeight);
    }
  }, [canvasSize, viewportRef, handleResetZoomPosition]);

  const centerPosition = useCallback(() => {
    if (viewportRef?.current) {
      const { offsetWidth, offsetHeight } = viewportRef.current;

      handleCenterOnSelectedTask(offsetWidth, offsetHeight);
    }
  }, [handleCenterOnSelectedTask, viewportRef]);

  useEffect(() => {
    if (viewportRef?.current) {
      const { offsetWidth, offsetHeight } = viewportRef.current;

      handleSetInitialViewportOffset(offsetWidth, offsetHeight);
    }
  }, [handleSetInitialViewportOffset, viewportRef]);

  useEffect(() => {
    centerPosition();
  }, [leftPanelExpanded, centerPosition]);

  usePinch(
    ({ offset: [factor], event }: any) => {
      event.stopPropagation();
      // This event needs to send the position of the mouse in the viewport. to handle zoom there
      // and should disable scroll events for a period of time.
      if (!isEventReallyWheel(event)) {
        const cursorPosition = getRelativeCursorPosition(event);

        handleSetZoomAndPosition(
          { x: cursorPosition.x, y: cursorPosition.y },
          factor,
        );
      }
    },
    {
      scaleBounds: { min: MIN_ZOOM, max: MAX_ZOOM },
      from: zoom,
      enabled: !!layout,
      target: viewportRef.current!,
      eventOptions: { passive: false },
    },
  );

  const scrollCallback = useCallback<Handler<"wheel", WheelEvent>>(
    ({ delta, event, metaKey, ctrlKey, direction }) => {
      event.stopPropagation();
      event.preventDefault();

      if ((metaKey || ctrlKey) && direction[1] !== 0) {
        const zoomSensitivity = 0.001; // Adjust this value to control zoom sensitivity
        let newZoom = zoom * (1 - event.deltaY * zoomSensitivity);

        if (newZoom < MIN_ZOOM) {
          newZoom = MIN_ZOOM;
        } else if (newZoom > MAX_ZOOM) {
          newZoom = MAX_ZOOM;
        }

        const cursorPosition = getRelativeCursorPosition(event);

        handleSetZoomAndPosition(
          { x: cursorPosition.x as number, y: cursorPosition.y },
          newZoom,
        );
      } else {
        const newX = position.x - delta[0];
        const newY = position.y - delta[1];
        handleSetPosition!({ x: newX, y: newY });
      }
    },
    [
      getRelativeCursorPosition,
      handleSetZoomAndPosition,
      handleSetPosition,
      zoom,
      position,
    ],
  );

  useWheel(scrollCallback, {
    enabled: !!layout,
    target: viewportRef.current!,
    eventOptions: { passive: false },
  });

  useDrag(
    (props: any) => {
      const { delta, event, tap } = props;
      event.stopPropagation();
      const newX = position.x + delta[0];
      const newY = position.y + delta[1];

      // Filter to prevent onClick event
      if (!tap) {
        handleDrag(
          { x: newX, y: newY },
          { x: event.clientX, y: event.clientY },
        );
      }
    },
    {
      target: viewportRef.current!,
      eventOptions: { passive: false },
      filterTaps: true,
    },
  );

  const fitToScreen = useCallback(() => {
    if (viewportRef?.current) {
      const { offsetWidth, offsetHeight } = viewportRef.current;

      handleSetFitScreen(offsetWidth, offsetHeight);
    }
  }, [viewportRef, handleSetFitScreen]);

  return (
    <Viewport
      viewportRef={viewportRef}
      cursor={panEnabled ? "grab" : "auto"}
      isInconsistent={isInconsistent}
    >
      <PanAndZoomContextProvider panAndZoomActor={panAndZoomActor}>
        <ZoomControls
          {...{
            zoom,
            setZoom: handleZoom,
            layout,
            resetPosition,
            isInconsistent,
            fitToScreen,
            printScreen: () => printScreen(workflowName || "workflow_diagram"),
          }}
          togglePan={handleTogglePan}
          panEnabled={panEnabled}
          flowActor={flowActor}
          isSearchFieldVisible={isSearchFieldVisible}
          toggleSearchField={handleToggleSearchField}
          isExecutionView={isExecutionView}
        />
        {viewPortChildren}
        <div id="workflow-diagram-outer">
          <div
            id="pan-and-zoom-wrapper-diagram-container"
            style={{
              position: "relative",
              transformOrigin: "top left",
              transition: "transform .1s",
              transform: `translateX(${position.x}px) translateY(${position.y}px) scale(${zoom})`,
              width: canvasSize.width, // this is the same size as the layout. only initialized
              height: canvasSize.height,
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
      </PanAndZoomContextProvider>
    </Viewport>
  );
};

export default PanAndZoomWrapper;
