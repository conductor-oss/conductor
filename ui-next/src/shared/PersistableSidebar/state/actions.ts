import { DoneInvokeEvent, assign } from "xstate";
import {
  AddMenuEventType,
  AddMenusEventType,
  RemoveMenuEventType,
  SidebarMachineContext,
  ToggleBannerEventType,
  ToggleSearchModalEventType,
} from "./types";
import _filter from "lodash/filter";
import { MENU_ITEMS_LOCAL_STORAGE_KEY } from "./services";

export const persistOpenedMenus = assign({
  openedMenus: (_context, { data }: DoneInvokeEvent<any>) => data,
});

export const persistBannerStateInContext = assign<
  SidebarMachineContext,
  ToggleBannerEventType
>((_context, { data }) => ({
  isBannerOpen: data.val,
}));

export const persistSearchModalStateInContext = assign<
  SidebarMachineContext,
  ToggleSearchModalEventType
>((_context, { data }) => ({
  isSearchModalOpen: data.val,
}));

export const clearMenuState = assign<SidebarMachineContext>(
  ({ openedMenus }) => ({
    stashedMenus: openedMenus,
    openedMenus: [],
  }),
);

export const restoreMenuState = assign<SidebarMachineContext>(
  ({ stashedMenus }) => ({
    openedMenus: stashedMenus,
    stashedMenus: [],
  }),
);

export const addInOpenedMenus = assign({
  openedMenus: (
    { openedMenus }: SidebarMachineContext,
    { data }: AddMenuEventType,
  ) => {
    const { id } = data;
    const existedMenu = openedMenus?.includes(id);
    if (!existedMenu) {
      const updatedMenus = [...openedMenus, id];
      localStorage.setItem(
        MENU_ITEMS_LOCAL_STORAGE_KEY,
        JSON.stringify(updatedMenus),
      );
      return updatedMenus;
    }
    return openedMenus;
  },
});

export const removeFromOpenedMenus = assign({
  openedMenus: (
    { openedMenus }: SidebarMachineContext,
    { data }: RemoveMenuEventType,
  ) => {
    const { id } = data;
    const updatedMenus = _filter(openedMenus, (menu) => menu !== id);
    localStorage.setItem(
      MENU_ITEMS_LOCAL_STORAGE_KEY,
      JSON.stringify(updatedMenus),
    );
    return updatedMenus;
  },
});

export const setOpenedMenus = assign({
  openedMenus: (
    _context: SidebarMachineContext,
    { data }: AddMenusEventType,
  ) => {
    const { items } = data;
    localStorage.setItem(MENU_ITEMS_LOCAL_STORAGE_KEY, JSON.stringify(items));
    return items;
  },
});
