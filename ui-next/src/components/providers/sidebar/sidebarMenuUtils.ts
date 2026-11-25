/**
 * Shared sidebar menu merge helpers (used by UiSidebar and default-home routing).
 */

import { MenuItemType } from "components/providers/sidebar/types";
import { SidebarItemRegistration } from "plugins/registry";

const POSITION_END = 99999;

/** Resolve position for sorting: number as-is, "start" => 0, "end" or undefined => end. */
export function sortPosition(
  position: SidebarItemRegistration["position"],
): number {
  if (position === "start") return 0;
  if (position === "end" || position === undefined) return POSITION_END;
  return Number(position);
}

/**
 * Convert a plugin SidebarItemRegistration to the MenuItemType format used by the Sidebar.
 */
export function pluginItemToMenuItem(
  item: SidebarItemRegistration,
): MenuItemType {
  return {
    id: item.id,
    title: item.title,
    icon: item.icon,
    linkTo: item.linkTo,
    activeRoutes: item.activeRoutes,
    activeSearchParams: item.activeSearchParams,
    shortcuts: item.shortcuts || [],
    hotkeys: item.hotkeys || "",
    hidden: item.hidden ?? false,
    isOpenNewTab: item.isOpenNewTab,
    textStyle: item.textStyle,
    buttonContainerStyle: item.buttonContainerStyle,
    iconContainerStyles: item.iconContainerStyles,
    handler: item.handler,
    component: item.component,
    position: Number(sortPosition(item.position)),
    items: item.items?.map(pluginItemToMenuItem),
    useBadgeCount: item.useBadgeCount,
  };
}

function upsertById(items: MenuItemType[], item: MenuItemType) {
  const idx = items.findIndex((i) => i.id === item.id);
  if (idx !== -1) items[idx] = item;
  else items.push(item);
}

function sortItemsByPosition(items: MenuItemType[]): MenuItemType[] {
  return [...items].sort(
    (a, b) =>
      Number(a.position ?? POSITION_END) - Number(b.position ?? POSITION_END),
  );
}

/** Recursively sort each level by position. */
export function sortMenuByPosition(items: MenuItemType[]): MenuItemType[] {
  return sortItemsByPosition(
    items.map((item) =>
      item.items?.length
        ? { ...item, items: sortMenuByPosition(item.items) }
        : item,
    ),
  );
}

/**
 * Merge plugin-registered sidebar items into the core menu structure.
 *
 * Plugin items can:
 * 1. Target a specific submenu (executionsSubMenu, definitionsSubMenu, etc.)
 * 2. Target "root" to add a new top-level menu item
 */
export function mergePluginSidebarItems(
  coreItems: MenuItemType[],
  pluginItems: SidebarItemRegistration[],
): MenuItemType[] {
  const result: MenuItemType[] = coreItems.map((item) => {
    const cloned: MenuItemType = { ...item, position: item.position };
    if (item.items) {
      cloned.items = [...item.items];
    }
    return cloned;
  });

  const itemsByTarget = new Map<string, SidebarItemRegistration[]>();
  for (const item of pluginItems) {
    const target = item.targetMenu;
    if (!itemsByTarget.has(target)) {
      itemsByTarget.set(target, []);
    }
    itemsByTarget.get(target)!.push(item);
  }

  for (const items of itemsByTarget.values()) {
    items.sort((a, b) => {
      const posA = a.position ?? "end";
      const posB = b.position ?? "end";

      if (posA === "start" && posB !== "start") return -1;
      if (posB === "start" && posA !== "start") return 1;
      if (posA === "end" && posB !== "end") return 1;
      if (posB === "end" && posA !== "end") return -1;

      if (typeof posA === "number" && typeof posB === "number") {
        return posA - posB;
      }

      return 0;
    });
  }

  for (const [targetId, items] of itemsByTarget.entries()) {
    for (const item of items) {
      const menuItem = pluginItemToMenuItem(item);
      if (targetId === "root") {
        upsertById(result, menuItem);
      } else {
        const targetMenu = result.find((i) => i.id === targetId);
        if (targetMenu?.items) upsertById(targetMenu.items, menuItem);
      }
    }
  }

  return sortMenuByPosition(result);
}
