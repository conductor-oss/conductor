import { tryToJson } from "utils/utils";

export const MENU_ITEMS_LOCAL_STORAGE_KEY = "menuItems";

const defaultOpenedMenus = [
  "executionsSubMenu",
  "definitionsSubMenu",
  "adminSubMenu",
];

export const getOpenedMenusFromLocalStorage = async () => {
  try {
    const openedMenusInLocalStorage = localStorage.getItem(
      MENU_ITEMS_LOCAL_STORAGE_KEY,
    );

    if (typeof openedMenusInLocalStorage === "string") {
      const parsedMenus = tryToJson(openedMenusInLocalStorage) as string[];

      if (Array.isArray(parsedMenus) && parsedMenus.length > 0) {
        return parsedMenus;
      }
    }
    return defaultOpenedMenus;
  } catch {
    return Promise.reject("Error while getting opened menus ");
  }
};
