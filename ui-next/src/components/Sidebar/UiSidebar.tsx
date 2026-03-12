/**
 * UiSidebar - Main sidebar component for Conductor UI
 *
 * This component defines the core (OSS) sidebar menu items and merges in
 * any additional items registered by plugins (enterprise features).
 *
 * Core OSS items:
 * - Executions submenu (Workflow, Queue Monitor)
 * - Run Workflow button
 * - Definitions submenu (Workflow, Task, Event Handler)
 * - API Docs
 * - Help menu
 *
 * Enterprise items are registered via plugins and merged at runtime.
 */

import { Sidebar } from "components/Sidebar";
import { useAnnouncementBanner } from "components/v1/layout/header/bannerUtils";
import { MenuItemType } from "components/Sidebar/types";
import { pluginRegistry, SidebarItemRegistration } from "plugins/registry";
import { FunctionComponent, useContext, useMemo } from "react";
import { FEATURES, featureFlags } from "utils";
import { SidebarContext } from "./context/SidebarContext";
import { useAuth } from "../../shared/auth";
import { getCoreSidebarItems } from "./sidebarCoreItems";

const customLogo = featureFlags.getValue(FEATURES.CUSTOM_LOGO_URL);

type UISidebarProps = {
  apiVersion?: string;
  releaseVersion?: string;
};

const POSITION_END = 99999;

/** Resolve position for sorting: number as-is, "start" => 0, "end" or undefined => end. Always returns a number. */
function sortPosition(position: SidebarItemRegistration["position"]): number {
  if (position === "start") return 0;
  if (position === "end" || position === undefined) return POSITION_END;
  return Number(position);
}

/**
 * Convert a plugin SidebarItemRegistration to the MenuItemType format used by the Sidebar component
 */
function pluginItemToMenuItem(item: SidebarItemRegistration): MenuItemType {
  return {
    id: item.id,
    title: item.title,
    icon: item.icon,
    linkTo: item.linkTo,
    activeRoutes: item.activeRoutes,
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

/** Sort items by position (undefined last). Uses Number() so comparison is always numeric. */
function sortItemsByPosition(items: MenuItemType[]): MenuItemType[] {
  return [...items].sort(
    (a, b) =>
      Number(a.position ?? POSITION_END) - Number(b.position ?? POSITION_END),
  );
}

/** Recursively sort each level by position. */
function sortMenuByPosition(items: MenuItemType[]): MenuItemType[] {
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
 * 1. Target a specific submenu (executionsSubMenu, definitionsSubMenu, etc.) to add items to it
 * 2. Target "root" to add a new top-level menu item
 *
 * Items are inserted based on their position hint (start, end, or numeric index).
 */
function mergePluginSidebarItems(
  coreItems: MenuItemType[],
  pluginItems: SidebarItemRegistration[],
): MenuItemType[] {
  // Clone core items to avoid mutation; explicitly preserve position for sort
  const result: MenuItemType[] = coreItems.map((item) => {
    const cloned: MenuItemType = { ...item, position: item.position };
    if (item.items) {
      cloned.items = [...item.items];
    }
    return cloned;
  });

  // Group plugin items by target menu
  const itemsByTarget = new Map<string, SidebarItemRegistration[]>();
  for (const item of pluginItems) {
    const target = item.targetMenu;
    if (!itemsByTarget.has(target)) {
      itemsByTarget.set(target, []);
    }
    itemsByTarget.get(target)!.push(item);
  }

  // Sort items within each target by position
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

  // Insert plugin items; final order is determined by sortMenuByPosition (position 10, 15, 20, ...)
  for (const [targetId, items] of itemsByTarget.entries()) {
    for (const item of items) {
      const menuItem = pluginItemToMenuItem(item);
      if (targetId === "root") {
        result.push(menuItem);
      } else {
        const targetMenu = result.find((i) => i.id === targetId);
        if (targetMenu && targetMenu.items) {
          targetMenu.items.push(menuItem);
        }
      }
    }
  }

  return sortMenuByPosition(result);
}

export const UISidebar: FunctionComponent<UISidebarProps> = ({
  apiVersion,
  releaseVersion,
}) => {
  const {
    open,
    setSearchModal,
    toggleMenu,
    isMobile,
    isBannerOpen,
    showAiStudioBanner,
  } = useContext(SidebarContext);

  const { isTrialExpired, trialExpiryDate, isAnnouncementBannerDismissed } =
    useAuth();
  const { showBanner } = useAnnouncementBanner(
    isTrialExpired,
    trialExpiryDate!,
    isAnnouncementBannerDismissed,
  );

  // Get plugin-registered sidebar items
  const pluginSidebarItems = useMemo(
    () => pluginRegistry.getSidebarItems(),
    [],
  );

  const menuItems = useMemo<MenuItemType[]>(() => {
    const coreItems = getCoreSidebarItems(open);
    return mergePluginSidebarItems(coreItems, pluginSidebarItems);
  }, [open, pluginSidebarItems]);

  return (
    <Sidebar
      menuItems={menuItems}
      customLogo={customLogo}
      apiVersion={apiVersion}
      releaseVersion={releaseVersion}
      open={open}
      toggleMenu={toggleMenu}
      isMobile={isMobile}
      isAnnouncementBannerVisible={
        (showBanner && isBannerOpen) || showAiStudioBanner
      }
      onSearchClick={() => setSearchModal(true)}
    />
  );
};
