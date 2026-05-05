import { WorkflowDef } from "types/WorkflowDef";
import { assign, DoneInvokeEvent, raise } from "xstate";
import {
  RichAddTaskMenuMachineContext,
  TypingEvent,
  TaskDefinition,
  SetHoveredItemEvent,
  SetSelectedTabEvent,
  RichAddMenuTabs,
  GotToEndEvent,
  BaseTaskMenuItem,
  RichAddTaskMenuEventTypes,
  ScrollToTopEvent,
  SetMenuTypeEvent,
  IntegrationMenuItem,
  FetchIntegrationToolsEvent,
  UpdateIntegrationDrillDownEvent,
} from "./types";
import { TaskType } from "types/common";
import _first from "lodash/first";
import { itemFilterMatcher } from "../helpers";
import { uniqueTaskIdGenerator } from "../taskGenerator";

// Type for raw integration data from API
interface RawIntegrationData {
  name: string;
  description?: string;
  type: string;
  iconName?: string;
  category?: string;
}

export const persistSearchQuery = assign<
  RichAddTaskMenuMachineContext,
  TypingEvent
>({
  searchQuery: (context, event) => event.text,
});

export const clearSearchQuery = assign<RichAddTaskMenuMachineContext>({
  searchQuery: "",
});

export const persistTaskDefinitions = assign<
  RichAddTaskMenuMachineContext,
  DoneInvokeEvent<TaskDefinition[]>
>((_context, event) => {
  return {
    taskDefinitions: event.data,
    workerMenuItems: event.data.map(
      (task): BaseTaskMenuItem => ({
        category: RichAddMenuTabs.WORKERS_TAB,
        name: task.name,
        description: task.description,
        type: TaskType.SIMPLE,
      }),
    ),
    isTaskDefFetched: true, // FIXME, remove flags.
  };
});

export const persistWorkflowDefinitions = assign<
  RichAddTaskMenuMachineContext,
  DoneInvokeEvent<WorkflowDef[]>
>((_context, event) => {
  return {
    workflowDefinitions: event.data,
    workflowMenuItems: event.data.map(
      (workflow): BaseTaskMenuItem => ({
        category: RichAddMenuTabs.SUB_WORKFLOWS_TAB,
        name: workflow.name,
        description: workflow.description,
        version: workflow.version,
        type: TaskType.SUB_WORKFLOW,
      }),
    ),
    isSubWfFetched: true,
  };
});

export const persistIntegrations = assign<
  RichAddTaskMenuMachineContext,
  DoneInvokeEvent<unknown>
>((context, event) => {
  const data = event.data as
    | {
        supportedIntegrations?: RawIntegrationData[];
        availableIntegrations?: RawIntegrationData[];
      }
    | undefined;

  // If the fetch failed or returned bad data, preserve existing integrations
  if (!data || (!data.supportedIntegrations && !data.availableIntegrations)) {
    console.warn(
      "[persistIntegrations] No integration data received, preserving existing state",
    );
    return {
      isIntegrationsFetched: true,
    };
  }

  // Get the types that are already in availableIntegrations
  const availableIntegrationTypes =
    data?.availableIntegrations?.map((integration) => integration.type) || [];

  return {
    integrationDefs:
      (data?.supportedIntegrations as never) ?? context.integrationDefs,
    supportedIntegrations:
      data?.supportedIntegrations?.map(
        (integration): IntegrationMenuItem => ({
          category: RichAddMenuTabs.INTEGRATIONS_TAB,
          name: integration.name,
          description: integration.description ?? "",
          type: TaskType.MCP,
          integrationType: integration.type,
          iconName: integration.iconName ?? "",
          status: availableIntegrationTypes.includes(integration.type)
            ? "active"
            : "inactive",
        }),
      ) ??
      context.supportedIntegrations ??
      [],
    availableIntegrations:
      data?.availableIntegrations?.map(
        (integration): IntegrationMenuItem => ({
          category: RichAddMenuTabs.INTEGRATIONS_TAB,
          name: integration.name,
          description: integration.description ?? "",
          type: TaskType.MCP,
          integrationType: integration.type,
          iconName:
            data?.supportedIntegrations?.find(
              (supportedIntegration) =>
                supportedIntegration.type === integration.type,
            )?.iconName ?? "",
          status: "active",
        }),
      ) ??
      context.availableIntegrations ??
      [],
    isIntegrationsFetched: true,
  };
});

export const persistHoveredItem = assign<
  RichAddTaskMenuMachineContext,
  SetHoveredItemEvent
>({
  hoveredItem: (context, event) => event.data,
});

export const persistSelectedTab = assign<
  RichAddTaskMenuMachineContext,
  SetSelectedTabEvent
>({
  selectedTab: (context, event) => event.tab,
});

export const setSelectedTabAll = assign<RichAddTaskMenuMachineContext>({
  selectedTab: RichAddMenuTabs.ALL_TAB,
});

export const persistLastScrollPosition = assign<
  RichAddTaskMenuMachineContext,
  GotToEndEvent
>({
  lastScrollTopPosition: (context, event) => event.lastScrollTopPosition,
});

export const updateToScrollTop = assign<RichAddTaskMenuMachineContext>({
  toScrollTop: (context) => context.lastScrollTopPosition,
});

export const persistToScrollTop = assign<RichAddTaskMenuMachineContext>({
  toScrollTop: () => 0,
  lastScrollTopPosition: () => 0,
});

export const resetToScrollTop = raise<
  RichAddTaskMenuMachineContext,
  ScrollToTopEvent
>(
  (__, _event) => ({
    type: RichAddTaskMenuEventTypes.SCROLL_TO_TOP,
  }),
  { delay: 100 },
);

export const preSelectTheFirstItem = assign<
  RichAddTaskMenuMachineContext,
  SetSelectedTabEvent
>({
  hoveredItem: (context) => {
    const allItems = context.baseTaskMenuItems
      .concat(context?.workerMenuItems ?? [])
      .concat(context?.workflowMenuItems);

    const finder = itemFilterMatcher(context.searchQuery, context.selectedTab);

    const firstItem = allItems.find(finder);

    return firstItem ? uniqueTaskIdGenerator(firstItem) : context.hoveredItem;
  },
});
export const hoverFirstItem = assign<RichAddTaskMenuMachineContext>({
  hoveredItem: (context) => {
    const firstAllTasks = _first(context.baseTaskMenuItems);
    return firstAllTasks
      ? uniqueTaskIdGenerator(firstAllTasks)
      : context.hoveredItem;
  },
});

export const persistMenuType = assign<
  RichAddTaskMenuMachineContext,
  SetMenuTypeEvent
>({
  menuType: (context, event) => event.menuType,
});

export const persistSelectedIntegration = assign<
  RichAddTaskMenuMachineContext,
  FetchIntegrationToolsEvent
>({
  integrationDrillDownMenu: (context, event) => ({
    ...context.integrationDrillDownMenu,
    selectedIntegration: event.integration,
  }),
});

// export const clearSelectedIntegration = assign<
//   RichAddTaskMenuMachineContext,
//   SetSelectedTabEvent
// >({
//   selectedIntegration: (_context) => undefined,
// });

export const persistSelectedIntegrationTools = assign<
  RichAddTaskMenuMachineContext,
  DoneInvokeEvent<Record<string, unknown>[]>
>({
  integrationDrillDownMenu: (context, event) => ({
    ...context.integrationDrillDownMenu,
    level: "tools",
    selectedIntegrationTools: event.data,
  }),
});

// export const clearSelectedIntegrationTools = assign<
//   RichAddTaskMenuMachineContext,
//   SetSelectedTabEvent
// >({
//   selectedIntegrationTools: (_context) => undefined,
// });

export const persistIntegrationDrillDown = assign<
  RichAddTaskMenuMachineContext,
  UpdateIntegrationDrillDownEvent
>({
  integrationDrillDownMenu: (_context, event) => event.data,
});

export const switchToAdvancedMenu = raise<
  RichAddTaskMenuMachineContext,
  SetMenuTypeEvent
>({
  type: RichAddTaskMenuEventTypes.SET_MENU_TYPE,
  menuType: "advanced",
});

export const switchSelectedTabToIntegrations = raise<
  RichAddTaskMenuMachineContext,
  SetSelectedTabEvent
>({
  type: RichAddTaskMenuEventTypes.SET_SELECTED_TAB,
  tab: RichAddMenuTabs.INTEGRATIONS_TAB,
});
