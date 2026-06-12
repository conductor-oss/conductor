import { createMachine } from "xstate";
import {
  PersistableSidebarEvent,
  PersistableSidebarEventTypes,
  PersistableSidebarStates,
  SidebarMachineContext,
} from "./types";
import * as actions from "./actions";
import * as services from "./services";

export const sidebarMachine = createMachine<
  SidebarMachineContext,
  PersistableSidebarEvent
>(
  {
    id: "sidebarMachine",
    predictableActionArguments: true,
    initial: PersistableSidebarStates.INIT,
    context: {
      openedMenus: [],
      stashedMenus: [],
      isBannerOpen: true,
      isSearchModalOpen: false,
    },
    on: {
      [PersistableSidebarEventTypes.TOGGLE_BANNER_EVENT]: {
        actions: "persistBannerStateInContext",
      },
      [PersistableSidebarEventTypes.TOGGLE_SEARCH_MODAL_EVENT]: {
        actions: "persistSearchModalStateInContext",
      },
      [PersistableSidebarEventTypes.ADD_MENU_EVENT]: {
        actions: "addInOpenedMenus",
      },
      [PersistableSidebarEventTypes.REMOVE_MENU_EVENT]: {
        actions: "removeFromOpenedMenus",
      },
      [PersistableSidebarEventTypes.ADD_MENUS_EVENT]: {
        actions: "setOpenedMenus",
      },
    },
    states: {
      [PersistableSidebarStates.INIT]: {
        invoke: {
          src: "getOpenedMenusFromLocalStorage",
          onDone: {
            target: PersistableSidebarStates.EXPANDED,
            actions: "persistOpenedMenus",
          },
          onError: {
            target: PersistableSidebarStates.EXPANDED,
          },
        },
      },
      [PersistableSidebarStates.EXPANDED]: {
        on: {
          [PersistableSidebarEventTypes.COLLAPSE_SIDEBAR_EVENT]: {
            actions: "clearMenuState",
            target: PersistableSidebarStates.COLLAPSED,
          },
        },
      },
      [PersistableSidebarStates.COLLAPSED]: {
        on: {
          [PersistableSidebarEventTypes.EXPAND_SIDEBAR_EVENT]: {
            actions: "restoreMenuState",
            target: PersistableSidebarStates.EXPANDED,
          },
        },
      },
    },
  },
  {
    actions: actions as any,
    services: services as any,
  },
);
