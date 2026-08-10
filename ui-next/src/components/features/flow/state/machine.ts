import { createMachine } from "xstate";
import * as actions from "./action";
import * as guards from "./guards";
import { updateWorkflowDefinitionService } from "./service";
import { panAndZoomMachine } from "../components/graphs/PanAndZoomWrapper";
import {
  richAddTaskMenuMachine,
  getALL_TASKS,
  RichAddMenuTabs,
  RichAddTaskMenuEventTypes,
} from "../components/RichAddTaskMenu";

import {
  FlowContext,
  FlowEvents,
  FlowStates,
  FlowActionTypes,
  FlowMachineStates,
} from "./types";

export const flowMachine = createMachine<FlowContext, FlowEvents, FlowStates>(
  {
    id: "flowMachine",
    predictableActionArguments: true,
    initial: FlowMachineStates.INIT,
    context: {
      authHeaders: undefined,
      currentWf: {},
      selectedNodeIdx: undefined,
      nodes: [],
      edges: [],
      menuOperationContext: undefined,
      openedNode: undefined,
      draggedNodeData: undefined,
      collapseWorkflowList: [],
    },
    states: {
      [FlowMachineStates.INIT]: {
        type: "parallel",
        states: {
          [FlowMachineStates.DIAGRAM_RENDERER]: {
            initial: "inconsistent",
            on: {
              [FlowActionTypes.SELECT_TASK_WITH_TASK_REF]: {
                actions: ["notifyTaskSelectionToParent"],
              },
              [FlowActionTypes.UPDATE_WF_DEFINITION_EVT]: {
                target: ".updatingWfDefintion",
                actions: ["maybeCleanSelection"],
              },
            },
            states: {
              inconsistent: {
                entry: "resetNodeSelection",
              },
              updatingWfDefintion: {
                invoke: {
                  id: "updateWorkflowDefinition",
                  src: "updateWorkflowDefinitionService",
                  onDone: {
                    actions: ["spreadData"],
                    target: FlowMachineStates.DIAGRAM_RENDERER_INIT,
                  },

                  onError: {
                    target: "inconsistent",
                    actions: ["notifyErrorToParent"],
                  },
                },
              },
              [FlowMachineStates.DIAGRAM_RENDERER_INIT]: {
                entry: ["cleanMenuOperationContext", "notifyFinishedRender"],
                initial: FlowMachineStates.DIAGRAM_RENDERER_MENU_CLOSED,
                on: {
                  [FlowActionTypes.SELECT_NODE_EVT]: {
                    actions: ["selectNode", "notifySelectionToParent"],
                  },
                  [FlowActionTypes.SELECT_TASK_WITH_TASK_REF]: {
                    actions: ["notifyTaskSelectionToParent"],
                  },
                  [FlowActionTypes.SELECT_NODE_INTERNAL_EVT]: {
                    actions: ["selectNode"],
                  },
                  [FlowActionTypes.OPEN_NODE_MENU_EVT]: {
                    actions: "toggleNodeMenu",
                  },
                  [FlowActionTypes.SET_READ_ONLY_EVT]: {
                    target: "inconsistent",
                  },
                  [FlowActionTypes.SELECT_EDGE_EVT]: {
                    actions: ["selectEdge", "notifySelectionToParent"],
                  },
                  [FlowActionTypes.UPDATE_COLLAPSE_WORKFLOW_LIST]: {
                    actions: ["updateCollapseWorkflowList"],
                  },
                  [RichAddTaskMenuEventTypes.SET_MENU_TYPE]: {
                    actions: ["sendToDefinitionMachine"],
                  },
                },
                states: {
                  [FlowMachineStates.DIAGRAM_RENDERER_MENU_OPENED]: {
                    invoke: {
                      id: "richAddTaskMenuMachine",
                      src: richAddTaskMenuMachine,
                      data: ({ authHeaders, menuOperationContext, nodes }) => ({
                        authHeaders,
                        operationContext: menuOperationContext,
                        taskDefinitions: [],
                        workflowDefinitions: [],
                        searchQuery: "",
                        nodes,
                        baseTaskMenuItems: getALL_TASKS(),
                        selectedTab: RichAddMenuTabs.ALL_TAB,
                        workerMenuItems: [],
                        workflowMenuItems: [],
                        menuType: "quick",
                      }),
                      onDone: {
                        actions: "cleanMenuOperationContext",
                        target: FlowMachineStates.DIAGRAM_RENDERER_MENU_CLOSED,
                      },
                    },
                  },
                  [FlowMachineStates.DIAGRAM_RENDERER_MENU_CLOSED]: {
                    entry: ["clearDraggedElement"],
                    on: {
                      [FlowActionTypes.OPEN_EDGE_MENU_EVT]: {
                        actions: "toggleEdgeMenu",
                        target: FlowMachineStates.DIAGRAM_RENDERER_MENU_OPENED,
                      },
                      [FlowActionTypes.DRAG_TASK_BEGIN]: {
                        actions: ["persistDraggedTask"],
                        target:
                          FlowMachineStates.DIAGRAM_RENDERER_BEGIN_DRAGGING,
                      },
                    },
                  },
                  [FlowMachineStates.DIAGRAM_RENDERER_BEGIN_DRAGGING]: {
                    initial: "draggingTask",
                    states: {
                      draggingTask: {
                        on: {
                          [FlowActionTypes.DRAG_TASK_END]: [
                            {
                              cond: "hasValidActiveAndCurrent",
                              target: `#flowMachine.${[
                                FlowMachineStates.INIT,
                                FlowMachineStates.DIAGRAM_RENDERER,
                                FlowMachineStates.DIAGRAM_RENDERER_INIT,
                              ].join(".")}`,
                              actions: ["sendMoveTask"],
                            },
                            {
                              target: `#flowMachine.${[
                                FlowMachineStates.INIT,
                                FlowMachineStates.DIAGRAM_RENDERER,
                                FlowMachineStates.DIAGRAM_RENDERER_INIT,
                              ].join(".")}`,
                            },
                          ],
                        },
                      },
                    },
                  },
                },
              },
            },
          },
          zoomControls: {
            initial: "opened",
            states: {
              opened: {
                on: {
                  [FlowActionTypes.SET_LAYOUT]: {
                    actions: ["forwardToZoom"],
                  },
                  [FlowActionTypes.SELECT_NODE_EVT]: {
                    actions: ["forwardToZoom"],
                  },
                  [FlowActionTypes.DRAG_TASK_BEGIN]: {
                    actions: ["forwardToZoom"],
                  },
                  [FlowActionTypes.DRAG_TASK_END]: {
                    actions: ["forwardToZoom"],
                  },
                  [FlowActionTypes.RESET_ZOOM_POSITION]: {
                    actions: ["forwardToZoom"],
                  },
                },
                invoke: {
                  src: panAndZoomMachine,
                  id: "panAndZoomMachine",
                },
              },
            },
          },
          cardDisplayType: {
            initial: "init",
            states: {
              init: {
                always: [
                  {
                    target: "showDescription",
                    cond: (context) => {
                      const currentWf = context.currentWf as any;
                      return currentWf?.isTemplateDetail === true;
                    },
                  },
                  {
                    target: "hideDescription",
                  },
                ],
              },
              showDescription: {
                tags: ["showDescription"],
                on: {
                  [FlowActionTypes.TOGGLE_SHOW_DESCRIPTION]: {
                    target: "hideDescription",
                  },
                },
              },
              hideDescription: {
                on: {
                  [FlowActionTypes.TOGGLE_SHOW_DESCRIPTION]: {
                    target: "showDescription",
                  },
                },
              },
            },
          },
        },
      },
    },
  },
  {
    actions: actions as any,
    services: {
      updateWorkflowDefinitionService,
    },
    guards: guards as any,
  },
);
