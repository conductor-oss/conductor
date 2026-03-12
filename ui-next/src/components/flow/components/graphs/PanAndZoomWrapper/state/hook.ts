import { useCallback } from "react";
import { useSelector } from "@xstate/react";
import { ActorRef } from "xstate";
import {
  PanAndZoomEvents,
  PanAndZoomEventTypes,
  PositionProps,
  PanAndZoomStates,
} from "./types";

export const usePanAndZoomActor = (
  panAndZoomActor: ActorRef<PanAndZoomEvents>,
) => {
  const send = panAndZoomActor.send;

  const handleResetZoomPosition = useCallback(
    (viewportOffsetWidth: number, viewportOffsetHeight: number) => {
      send({
        type: PanAndZoomEventTypes.RESET_ZOOM_POSITION_EVT,
        viewportOffsetWidth,
        viewportOffsetHeight,
      });
    },
    [send],
  );

  const handleSetZoom = useCallback(
    (zoom: number) => {
      send({ type: PanAndZoomEventTypes.SET_ZOOM_EVT, zoom });
    },
    [send],
  );

  const handleSetPosition = useCallback(
    (position: PositionProps) => {
      send({ type: PanAndZoomEventTypes.SET_POSITION_EVT, position });
    },
    [send],
  );

  const handleDrag = useCallback(
    (position: PositionProps, clientMousePosition: PositionProps) => {
      send({
        type: PanAndZoomEventTypes.DRAG_EVENT_EVT,
        position,
        clientMousePosition,
      });
    },
    [send],
  );

  const handleCenterOnSelectedTask = useCallback(
    (viewportOffsetWidth: number, viewportOffsetHeight: number) => {
      send({
        type: PanAndZoomEventTypes.CENTER_ON_SELECTED_TASK,
        viewportOffsetWidth,
        viewportOffsetHeight,
      });
    },
    [send],
  );

  const handleSetFullScreen = useCallback(
    (fullScreen: boolean, viewportOffsetWidth: number) => {
      send({
        type: PanAndZoomEventTypes.SET_FULL_SCREEN_EVT,
        viewportOffsetWidth,
        fullScreen,
      });
    },
    [send],
  );

  const handleSetFitScreen = useCallback(
    (viewportOffsetWidth: number, viewportOffsetHeight: number) => {
      send({
        type: PanAndZoomEventTypes.SET_FIT_SCREEN_EVT,
        viewportOffsetWidth,
        viewportOffsetHeight,
      });
    },
    [send],
  );

  const handleSetInitialViewportOffset = useCallback(
    (viewportOffsetWidth: number, viewportOffsetHeight: number) => {
      send({
        type: PanAndZoomEventTypes.SET_INITIAL_VIEWPORT_OFFSET,
        viewportOffsetWidth,
        viewportOffsetHeight,
      });
    },
    [send],
  );

  const handleZoom = useCallback(
    (isZoomOut: boolean) => {
      send({
        type: PanAndZoomEventTypes.HANDLE_ZOOM_EVT,
        isZoomOut,
      });
    },
    [send],
  );

  const handleTogglePan = useCallback(
    () => send({ type: PanAndZoomEventTypes.TOGGLE_PAN_EVT }),
    [send],
  );

  const handleSetZoomAndPosition = useCallback(
    (position: PositionProps, zoom: number) =>
      send({
        type: PanAndZoomEventTypes.SET_ZOOM_TO_POSITION_EVT,
        zoom,
        position,
      }),
    [send],
  );

  const handleToggleSearchField = useCallback(
    () => send({ type: PanAndZoomEventTypes.TOGGLE_SEARCH_EVT }),
    [send],
  );

  const handleSelectSearchResult = useCallback(
    (viewportOffsetWidth: number, viewportOffsetHeight: number) =>
      send({
        type: PanAndZoomEventTypes.SELECT_SEARCH_RESULT,
        viewportOffsetWidth,
        viewportOffsetHeight,
      }),
    [send],
  );

  const handleSetEventType = useCallback(
    (eventType: string) =>
      send({ type: PanAndZoomEventTypes.SET_NOTIFIED_EVENT_TYPE, eventType }),
    [send],
  );

  return [
    {
      zoom: useSelector(panAndZoomActor, (state) => state.context.zoom),
      canvasSize: useSelector(
        panAndZoomActor,
        (state) => state.context.canvasSize,
      ),
      layout: useSelector(panAndZoomActor, (state) => state.context.layout),
      position: useSelector(panAndZoomActor, (state) => state.context.position),
      panEnabled: useSelector(panAndZoomActor, (state) =>
        state.matches([
          PanAndZoomStates.IDLE,
          PanAndZoomStates.PAN,
          PanAndZoomStates.PAN_ENABLED,
        ]),
      ),
      viewportSize: useSelector(
        panAndZoomActor,
        (state) => state.context.viewportSize,
      ),
      isSearchFieldVisible: useSelector(panAndZoomActor, (state) =>
        state.matches([
          PanAndZoomStates.IDLE,
          PanAndZoomStates.SEARCH_FIELD,
          PanAndZoomStates.SEARCH_FIELD_VISIBLE,
        ]),
      ),
      isPanAndZoomIdle: useSelector(panAndZoomActor, (state) =>
        state.matches([PanAndZoomStates.IDLE]),
      ),
      notifiedEventType: useSelector(
        panAndZoomActor,
        (state) => state.context.notifiedEventType,
      ),
    },
    {
      handleResetZoomPosition,
      handleSetZoom,
      handleSetPosition,
      handleCenterOnSelectedTask,
      handleSetInitialViewportOffset,
      handleSetFullScreen,
      handleSetFitScreen,
      handleZoom,
      handleTogglePan,
      handleDrag,
      handleSetZoomAndPosition,
      handleToggleSearchField,
      handleSelectSearchResult,
      handleSetEventType,
    },
  ] as const;
};
