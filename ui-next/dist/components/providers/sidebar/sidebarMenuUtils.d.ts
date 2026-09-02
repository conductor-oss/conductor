/**
 * Shared sidebar menu merge helpers (used by UiSidebar and default-home routing).
 */
import { MenuItemType } from "components/providers/sidebar/types";
import { SidebarItemRegistration } from "plugins/registry";
/** Resolve position for sorting: number as-is, "start" => 0, "end" or undefined => end. */
export declare function sortPosition(position: SidebarItemRegistration["position"]): number;
/**
 * Convert a plugin SidebarItemRegistration to the MenuItemType format used by the Sidebar.
 */
export declare function pluginItemToMenuItem(item: SidebarItemRegistration): MenuItemType;
/** Recursively sort each level by position. */
export declare function sortMenuByPosition(items: MenuItemType[]): MenuItemType[];
/**
 * Merge plugin-registered sidebar items into the core menu structure.
 *
 * Plugin items can:
 * 1. Target a specific submenu (executionsSubMenu, definitionsSubMenu, etc.)
 * 2. Target "root" to add a new top-level menu item
 */
export declare function mergePluginSidebarItems(coreItems: MenuItemType[], pluginItems: SidebarItemRegistration[]): MenuItemType[];
