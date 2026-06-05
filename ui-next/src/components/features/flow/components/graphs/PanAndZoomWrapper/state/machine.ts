import { createMachine } from "xstate";
import * as actions from "./actions";
import _isEmpty from "lodash/isEmpty";
import {
  PanAndZoomMachineContext,
  PanAndZoomEventTypes,
  PanAndZoomEvents,
  PanAndZoomStates,
} from "./types";
import { INITIAL_ZOOM } from "../constants";

const NO_SIZE = { width: 0, height: 0 };
const INITIAL_POSITION = { x: 0, y: 0 };

export const panAndZoomMachine = createMachine<
  PanAndZoomMachineContext,
  PanAndZoomEvents
>(
  {
    id: "panAndZoomMachine",
    predictableActionArguments: true,
    initial: PanAndZoomStates.INIT,
    context: {
      zoom: INITIAL_ZOOM,
      canvasSize: NO_SIZE,
      viewportSize: NO_SIZE,
      position: INITIAL_POSITION,
      isFullScreen: false,
      draggingUpdatedPosition: false,
      notifiedEventType: "",
    },
    states: {
      [PanAndZoomStates.INIT]: {
        on: {
          [PanAndZoomEventTypes.SET_LAYOUT_EVT]: {
            actions: "setLayout",
            target: "checkIfReady",
          },
          [PanAndZoomEventTypes.SET_INITIAL_VIEWPORT_OFFSET]: {
            actions: "setInitialViewportOffset",
            target: "checkIfReady",
          },
        },
      },
      [PanAndZoomStates.CHECK_IF_READY]: {
        always: [
          {
            target: PanAndZoomStates.INIT,
            cond: ({ layout, lastViewportOffsetWidth }) =>
              _isEmpty(layout?.children) || lastViewportOffsetWidth == null,
          },
          { actions: "resetZoomPosition", target: PanAndZoomStates.IDLE },
        ],
      },
      [PanAndZoomStates.IDLE]: {
        on: {
          [PanAndZoomEventTypes.RESET_ZOOM_POSITION_EVT]: {
            actions: "resetZoomPosition",
          },
          [PanAndZoomEventTypes.SET_ZOOM_EVT]: {
            actions: "setZoom",
          },
          [PanAndZoomEventTypes.SET_FIT_SCREEN_EVT]: {
            actions: ["fitToScreen"],
          },
          [PanAndZoomEventTypes.HANDLE_ZOOM_EVT]: {
            actions: ["handleZoom"],
          },
          [PanAndZoomEventTypes.SET_ZOOM_TO_POSITION_EVT]: {
            actions: "setZoomToPosition",
          },
          [PanAndZoomEventTypes.CENTER_ON_SELECTED_TASK]: {
            actions: "centerOnSelectedTask",
          },
          [PanAndZoomEventTypes.SELECT_NODE_EVENT_EVT]: {
            actions: ["setSelectedNode"],
          },
          [PanAndZoomEventTypes.SET_LAYOUT_EVT]: {
            actions: ["setLayout"],
          },
          [PanAndZoomEventTypes.SET_POSITION_EVT]: {
            actions: "setPosition",
          },
          [PanAndZoomEventTypes.SELECT_SEARCH_RESULT]: {
            actions: ["centerOnSelectedTask", "fireToggleSearchField"],
          },
          [PanAndZoomEventTypes.SET_NOTIFIED_EVENT_TYPE]: {
            actions: "setNotifiedEventType",
          },
        },
        type: "parallel",
        states: {
          [PanAndZoomStates.PAN]: {
            initial: PanAndZoomStates.PAN_ENABLED,
            states: {
              [PanAndZoomStates.PAN_ENABLED]: {
                on: {
                  [PanAndZoomEventTypes.DRAG_EVENT_EVT]: {
                    actions: ["setPosition"],
                  },
                  [PanAndZoomEventTypes.TOGGLE_PAN_EVT]: {
                    target: PanAndZoomStates.PAN_DISABLED,
                  },
                },
              },
              [PanAndZoomStates.PAN_DISABLED]: {
                on: {
                  [PanAndZoomEventTypes.TOGGLE_PAN_EVT]: {
                    target: PanAndZoomStates.PAN_ENABLED,
                  },
                },
                initial: PanAndZoomStates.NOT_DRAGGING_TASK,
                states: {
                  [PanAndZoomStates.DRAGGING_TASK]: {
                    on: {
                      [PanAndZoomEventTypes.DRAG_EVENT_EVT]: {
                        actions: ["setPositionOfDraggingTask"],
                      },
                      [PanAndZoomEventTypes.DRAG_TASK_END]: {
                        target: PanAndZoomStates.NOT_DRAGGING_TASK,
                        actions: ["cleanUpPositionUpdatedFlag"],
                      },
                    },
                  },
                  [PanAndZoomStates.NOT_DRAGGING_TASK]: {
                    on: {
                      [PanAndZoomEventTypes.DRAG_TASK_BEGIN]: {
                        target: PanAndZoomStates.DRAGGING_TASK,
                      },
                    },
                  },
                },
              },
            },
          },
          [PanAndZoomStates.SEARCH_FIELD]: {
            initial: PanAndZoomStates.SEARCH_FIELD_HIDDEN,
            states: {
              [PanAndZoomStates.SEARCH_FIELD_VISIBLE]: {
                on: {
                  [PanAndZoomEventTypes.TOGGLE_SEARCH_EVT]: {
                    target: PanAndZoomStates.SEARCH_FIELD_HIDDEN,
                  },
                },
              },
              [PanAndZoomStates.SEARCH_FIELD_HIDDEN]: {
                on: {
                  [PanAndZoomEventTypes.TOGGLE_SEARCH_EVT]: {
                    target: PanAndZoomStates.SEARCH_FIELD_VISIBLE,
                  },
                },
              },
            },
          },

          // pan
          // pan enabled
          // pan desabled

          // searchfield
          // visible
          // not visible
        },
      },
    },
  },
  {
    actions: actions as any,
  },
);
