import { ReactElement } from "react";
import { NodeData, PortData } from "reaflow";
import { AuthHeaders, FormTaskType, IntegrationDef, WorkflowDef } from "types";

export type TaskDefinition = {
  name: string;
  description: string;
};

export type OperationContextData = {
  id: string;
  port: PortData;
  node: NodeData;
};

export type BaseTaskMenuItem = {
  category: string;
  name: string;
  description: string;
  type: FormTaskType;
  version?: number;
  flagHidden?: boolean;
};

export type IntegrationMenuItem = BaseTaskMenuItem & {
  integrationType: string;
  iconName: string;
  status?: string;
};

export type TaskMenuItem = BaseTaskMenuItem & {
  name: string;
  description: string;
  onClick: () => void;
  icon: ReactElement;
  category?: string;
  version?: number;
};

export enum RichAddMenuTabs {
  ALL_TAB = "ALL",
  SYSTEMS_TAB = "System",
  OPERATORS_TAB = "Operators",
  ALERTING_TAB = "Alerting",
  WORKERS_TAB = "Workers",
  AI_AGENTS_TAB = "AI Tasks",
  SUB_WORKFLOWS_TAB = "Sub Workflows",
  INTEGRATIONS_TAB = "Integrations",
}

export type IntegrationDrillDownMenuProp = {
  isOpen: boolean;
  selectedIntegration: IntegrationMenuItem | null;
  selectedRootIntegration: IntegrationMenuItem | null;
  level: "integrations" | "tools";
  selectedIntegrationTools?: Record<string, any>[];
};

export interface RichAddTaskMenuMachineContext {
  taskDefinitions: TaskDefinition[];
  workflowDefinitions: WorkflowDef[];
  workerMenuItems: BaseTaskMenuItem[];
  workflowMenuItems: BaseTaskMenuItem[];
  operationContext?: OperationContextData;
  authHeaders?: AuthHeaders;
  searchQuery: string;
  nodes: NodeData[];
  hoveredItem: string;
  selectedTab: RichAddMenuTabs;
  isTaskDefFetched: boolean;
  isSubWfFetched: boolean;
  isIntegrationsFetched: boolean;
  lastScrollTopPosition: number;
  toScrollTop: number;
  baseTaskMenuItems: BaseTaskMenuItem[];
  menuType: "quick" | "advanced";
  supportedIntegrations: IntegrationMenuItem[];
  availableIntegrations: IntegrationMenuItem[];
  integrationDefs?: IntegrationDef[];
  integrationDrillDownMenu: IntegrationDrillDownMenuProp;
}

export enum MainStates {
  INIT = "init",
  CLOSED = "closed",
  IDLE = "idle",
  FETCH_FOR_TASK_DEFINITIONS = "fetchForTaskDefinitions",
  FETCH_FOR_WORKFLOW_DEFINITIONS = "fetchForWorkflowDefinitions",
  FETCH_FOR_INTEGRATIONS = "fetchForIntegrations",
  WITH_TASK_DEFINITIONS = "withTaskDefinitions",
  WITH_WORKFLOW_DEFINITIONS = "withWorkflowDefinitions",
  WITH_INTEGRATIONS = "withIntegrations",
  SEARCH_FIELD = "searchField",
  FETCH_FOR_INTEGRATION_TOOLS = "fetchForIntegrationTools",
}

export enum RichAddTaskMenuEventTypes {
  TYPING = "TYPING",
  CLOSE_MENU = "CLOSE_MENU",
  GOT_TO_END = "GOT_TO_END",
  SET_HOVERED_ITEM = "SET_HOVERED_ITEM",
  SET_SELECTED_TAB = "SET_SELECTED_TAB",
  SCROLL_TO_TOP = "SCROLL_TO_TOP",
  SET_MENU_TYPE = "SET_MENU_TYPE",
  FETCH_INTEGRATION_TOOLS = "FETCH_INTEGRATION_TOOLS",
  SET_SELECTED_INTEGRATION = "SET_SELECTED_INTEGRATION",
  REFETCH_INTEGRATIONS = "REFETCH_INTEGRATIONS",
  UPDATE_INTEGRATION_DRILL_DOWN = "UPDATE_INTEGRATION_DRILL_DOWN",
  SWITCH_TO_INTEGRATIONS = "SWITCH_TO_INTEGRATIONS",
}

export type TypingEvent = {
  type: RichAddTaskMenuEventTypes.TYPING;
  text: string;
};

export type CloseMenuEvent = {
  type: RichAddTaskMenuEventTypes.CLOSE_MENU;
};

export type GotToEndEvent = {
  type: RichAddTaskMenuEventTypes.GOT_TO_END;
  lastScrollTopPosition: number;
};
export type ScrollToTopEvent = {
  type: RichAddTaskMenuEventTypes.SCROLL_TO_TOP;
};

export type SetHoveredItemEvent = {
  type: RichAddTaskMenuEventTypes.SET_HOVERED_ITEM;
  data: string;
};

export type SetSelectedTabEvent = {
  type: RichAddTaskMenuEventTypes.SET_SELECTED_TAB;
  tab: RichAddMenuTabs;
};

export type SetMenuTypeEvent = {
  type: RichAddTaskMenuEventTypes.SET_MENU_TYPE;
  menuType: "quick" | "advanced";
};

export type FetchIntegrationToolsEvent = {
  type: RichAddTaskMenuEventTypes.FETCH_INTEGRATION_TOOLS;
  integration: IntegrationMenuItem;
};

export type RefetchIntegrationsEvent = {
  type: RichAddTaskMenuEventTypes.REFETCH_INTEGRATIONS;
};

export type UpdateIntegrationDrillDownEvent = {
  type: RichAddTaskMenuEventTypes.UPDATE_INTEGRATION_DRILL_DOWN;
  data: IntegrationDrillDownMenuProp;
};

export type SwitchToIntegrationsEvent = {
  type: RichAddTaskMenuEventTypes.SWITCH_TO_INTEGRATIONS;
};

export type RichAddTaskMenuEvents =
  | TypingEvent
  | CloseMenuEvent
  | GotToEndEvent
  | SetHoveredItemEvent
  | SetSelectedTabEvent
  | ScrollToTopEvent
  | SetMenuTypeEvent
  | FetchIntegrationToolsEvent
  | RefetchIntegrationsEvent
  | UpdateIntegrationDrillDownEvent
  | SwitchToIntegrationsEvent;
