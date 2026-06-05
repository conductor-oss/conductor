import { createMachine, sendParent } from "xstate";
import {
  RichAddTaskMenuMachineContext,
  MainStates,
  RichAddTaskMenuEventTypes,
  RichAddTaskMenuEvents,
  RichAddMenuTabs,
} from "./types";
import * as actions from "./actions";
import * as services from "./services";
import * as guards from "./guards";

const MINIMUM_CHARS_TO_SEARCH = 3;

export const richAddTaskMenuMachine = createMachine<
  RichAddTaskMenuMachineContext,
  RichAddTaskMenuEvents
>(
  {
    id: "richAddTaskMenuMachine",
    initial: "init",
    predictableActionArguments: true,
    context: {
      taskDefinitions: [],
      workflowDefinitions: [],
      authHeaders: undefined,
      operationContext: undefined,
      searchQuery: "",
      nodes: [],
      hoveredItem: "",
      selectedTab: RichAddMenuTabs.ALL_TAB,
      isSubWfFetched: false,
      isTaskDefFetched: false,
      isIntegrationsFetched: false,
      lastScrollTopPosition: 0,
      toScrollTop: 0,
      baseTaskMenuItems: [],
      workerMenuItems: [],
      workflowMenuItems: [],
      supportedIntegrations: [],
      availableIntegrations: [],
      menuType: "quick",
      integrationDrillDownMenu: {
        isOpen: false,
        selectedIntegration: null,
        selectedRootIntegration: null,
        level: "integrations",
      },
    },

    states: {
      init: {
        type: "parallel",
        states: {
          main: {
            initial: MainStates.INIT,
            on: {
              [RichAddTaskMenuEventTypes.CLOSE_MENU]: {
                actions: ["setSelectedTabAll"],
                target: `#richAddTaskMenuMachine.init.main.${MainStates.CLOSED}`,
              },
              [RichAddTaskMenuEventTypes.SET_HOVERED_ITEM]: {
                actions: ["persistHoveredItem"],
              },
              [RichAddTaskMenuEventTypes.SET_SELECTED_TAB]: [
                {
                  cond: (context, event) =>
                    guards.isTabIsWorkers(context, event) &&
                    guards.isTaskDefNotFetched(context),
                  actions: ["persistSelectedTab"],
                  target: `#richAddTaskMenuMachine.init.main.${MainStates.FETCH_FOR_TASK_DEFINITIONS}`,
                },

                {
                  cond: (context, event) =>
                    guards.isTabIsIntegrations(context, event) &&
                    guards.isIntegrationsNotFetched(context),
                  actions: ["persistSelectedTab"],
                  target: `#richAddTaskMenuMachine.init.main.${MainStates.FETCH_FOR_INTEGRATIONS}`,
                },
                {
                  actions: [
                    "persistSelectedTab",
                    "preSelectTheFirstItem",
                    "persistToScrollTop",
                  ],
                },
              ],
              [RichAddTaskMenuEventTypes.SET_MENU_TYPE]: [
                {
                  cond: (_, event) => event.menuType === "advanced",
                  actions: ["persistMenuType", sendParent((_, event) => event)],
                },
                {
                  actions: ["persistMenuType"],
                },
              ],
              [RichAddTaskMenuEventTypes.SWITCH_TO_INTEGRATIONS]: {
                actions: [
                  "clearSearchQuery",
                  "switchToAdvancedMenu",
                  "switchSelectedTabToIntegrations",
                ],
              },
            },
            states: {
              [MainStates.INIT]: {
                entry: "hoverFirstItem",
                always: {
                  target: MainStates.IDLE,
                },
              },
              [MainStates.CLOSED]: {
                always: {
                  target: "#richAddTaskMenuMachine.final",
                },
              },
              [MainStates.IDLE]: {
                after: {
                  500: {
                    target: MainStates.FETCH_FOR_TASK_DEFINITIONS,
                    cond: (context) => {
                      const result =
                        context.searchQuery.length > MINIMUM_CHARS_TO_SEARCH;
                      return result;
                    },
                  },
                },
                on: {
                  [RichAddTaskMenuEventTypes.TYPING]: {
                    target: MainStates.IDLE,
                    actions: "preSelectTheFirstItem",
                  },
                  [RichAddTaskMenuEventTypes.GOT_TO_END]: {
                    actions: "persistLastScrollPosition",
                    target: "fetchForTaskDefinitions",
                  },
                },
              },
              [MainStates.FETCH_FOR_TASK_DEFINITIONS]: {
                invoke: {
                  id: "fetchForTaskDefinitions",
                  src: "fetchForTaskDefinitions",
                  onDone: {
                    actions: "persistTaskDefinitions",
                    target: MainStates.WITH_TASK_DEFINITIONS,
                  },
                },
              },

              [MainStates.FETCH_FOR_INTEGRATIONS]: {
                invoke: {
                  id: "fetchForMCPIntegrations",
                  src: "fetchForMCPIntegrations",
                  onDone: {
                    actions: "persistIntegrations",
                    target: MainStates.WITH_INTEGRATIONS,
                  },
                  onError: {
                    // On error, still transition to WITH_INTEGRATIONS but keep existing data
                    target: MainStates.WITH_INTEGRATIONS,
                  },
                },
              },
              [MainStates.WITH_TASK_DEFINITIONS]: {
                after: {
                  500: {
                    target: MainStates.FETCH_FOR_INTEGRATIONS,
                    cond: (context) =>
                      context.searchQuery.length > MINIMUM_CHARS_TO_SEARCH + 1, // test event type
                  },
                },
                entry: ["updateToScrollTop", "preSelectTheFirstItem"],
                on: {
                  [RichAddTaskMenuEventTypes.SET_SELECTED_TAB]: [
                    {
                      cond: (context, event) =>
                        guards.isTabIsIntegrations(context, event) &&
                        !guards.isIntegrationsNotFetched(context),
                      target: MainStates.WITH_INTEGRATIONS,
                      actions: ["persistSelectedTab"],
                    },
                  ],
                  [RichAddTaskMenuEventTypes.TYPING]: {
                    target: MainStates.WITH_TASK_DEFINITIONS,
                  },
                  [RichAddTaskMenuEventTypes.GOT_TO_END]: {
                    target: MainStates.FETCH_FOR_INTEGRATIONS,
                    actions: "persistLastScrollPosition",
                  },
                },
              },
              [MainStates.WITH_WORKFLOW_DEFINITIONS]: {
                after: {
                  500: {
                    target: MainStates.FETCH_FOR_INTEGRATIONS,
                    cond: (context) =>
                      context.searchQuery.length > MINIMUM_CHARS_TO_SEARCH + 1,
                  },
                },
                entry: ["updateToScrollTop", "preSelectTheFirstItem"],
                on: {
                  [RichAddTaskMenuEventTypes.SET_SELECTED_TAB]: [
                    {
                      cond: (context, event) =>
                        guards.isTabIsWorkers(context, event) &&
                        guards.isTaskDefNotFetched(context),
                      target: MainStates.FETCH_FOR_TASK_DEFINITIONS,
                      actions: ["persistSelectedTab"],
                    },
                    {
                      cond: (context, event) =>
                        guards.isTabIsIntegrations(context, event) &&
                        guards.isIntegrationsNotFetched(context),
                      target: MainStates.FETCH_FOR_INTEGRATIONS,
                      actions: ["persistSelectedTab"],
                    },
                    {
                      cond: (context, event) =>
                        guards.isTabIsIntegrations(context, event) &&
                        !guards.isIntegrationsNotFetched(context),
                      target: MainStates.WITH_INTEGRATIONS,
                      actions: ["persistSelectedTab"],
                    },
                  ],
                  [RichAddTaskMenuEventTypes.GOT_TO_END]: {
                    target: MainStates.FETCH_FOR_INTEGRATIONS,
                    actions: "persistLastScrollPosition",
                  },
                },
              },
              [MainStates.WITH_INTEGRATIONS]: {
                // Nothing to do here we rendered everything.
                entry: ["updateToScrollTop", "preSelectTheFirstItem"],
                on: {
                  [RichAddTaskMenuEventTypes.SET_SELECTED_TAB]: [
                    {
                      cond: (context, event) =>
                        guards.isTabIsWorkers(context, event) &&
                        guards.isTaskDefNotFetched(context),
                      target: MainStates.FETCH_FOR_TASK_DEFINITIONS,
                      actions: ["persistSelectedTab"],
                    },
                  ],
                  [RichAddTaskMenuEventTypes.FETCH_INTEGRATION_TOOLS]: [
                    {
                      actions: ["persistSelectedIntegration"],
                      target: MainStates.FETCH_FOR_INTEGRATION_TOOLS,
                    },
                  ],
                  [RichAddTaskMenuEventTypes.UPDATE_INTEGRATION_DRILL_DOWN]: {
                    actions: ["persistIntegrationDrillDown"],
                    target: MainStates.WITH_INTEGRATIONS,
                  },
                  [RichAddTaskMenuEventTypes.REFETCH_INTEGRATIONS]: {
                    target: MainStates.FETCH_FOR_INTEGRATIONS,
                  },
                },
              },
              [MainStates.FETCH_FOR_INTEGRATION_TOOLS]: {
                invoke: {
                  id: "fetchForIntegrationTools",
                  src: "fetchForIntegrationTools",
                  onDone: {
                    actions: "persistSelectedIntegrationTools",
                    target: MainStates.WITH_INTEGRATIONS,
                  },
                  onError: {
                    target: MainStates.WITH_INTEGRATIONS,
                  },
                },
              },
            },
          },
          [MainStates.SEARCH_FIELD]: {
            on: {
              [RichAddTaskMenuEventTypes.TYPING]: {
                actions: ["persistSearchQuery", "preSelectTheFirstItem"],
              },
            },
          },
        },
      },
      final: {
        type: "final",
      },
    },
  },
  {
    services,
    actions: actions as any,
    guards: guards as any,
  },
);
