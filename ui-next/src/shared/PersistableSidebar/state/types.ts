export interface SidebarMachineContext {
  openedMenus: string[];
  stashedMenus: string[];
  isBannerOpen: boolean;
  isSearchModalOpen: boolean;
}

export enum PersistableSidebarStates {
  INIT = "INIT",
  EXPANDED = "EXPANDED",
  COLLAPSED = "COLLAPSED",
}

export enum PersistableSidebarEventTypes {
  TOGGLE_BANNER_EVENT = "TOGGLE_BANNER_EVENT",
  TOGGLE_SEARCH_MODAL_EVENT = "TOGGLE_SEARCH_MODAL_EVENT",
  COLLAPSE_SIDEBAR_EVENT = "COLLAPSE_SIDEBAR_EVENT",
  EXPAND_SIDEBAR_EVENT = "EXPAND_SIDEBAR_EVENT",
  ADD_MENU_EVENT = "ADD_MENU_EVENT",
  REMOVE_MENU_EVENT = "REMOVE_MENU_EVENT",
  ADD_MENUS_EVENT = "ADD_MENUS_EVENT",
}

export type ToggleBannerEventType = {
  type: PersistableSidebarEventTypes.TOGGLE_BANNER_EVENT;
  data: {
    val: boolean;
  };
};

export type ToggleSearchModalEventType = {
  type: PersistableSidebarEventTypes.TOGGLE_SEARCH_MODAL_EVENT;
  data: {
    val: boolean;
  };
};

export type CollapseSidebarEventType = {
  type: PersistableSidebarEventTypes.COLLAPSE_SIDEBAR_EVENT;
};

export type ExpandSidebarEventType = {
  type: PersistableSidebarEventTypes.EXPAND_SIDEBAR_EVENT;
};

export type AddMenuEventType = {
  type: PersistableSidebarEventTypes.ADD_MENU_EVENT;
  data: { id: string };
};

export type RemoveMenuEventType = {
  type: PersistableSidebarEventTypes.REMOVE_MENU_EVENT;
  data: { id: string };
};

export type AddMenusEventType = {
  type: PersistableSidebarEventTypes.ADD_MENUS_EVENT;
  data: { items: string[] };
};

export type PersistableSidebarEvent =
  | ToggleBannerEventType
  | ToggleSearchModalEventType
  | CollapseSidebarEventType
  | ExpandSidebarEventType
  | AddMenuEventType
  | RemoveMenuEventType
  | AddMenusEventType;
