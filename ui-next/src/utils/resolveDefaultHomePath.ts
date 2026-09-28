/**
 * Resolve the app "home" path from feature flags + the visible sidebar menu.
 *
 * - Playground: `/` (Launch Pad / Hub — registered as the public index)
 * - Otherwise: first in-app navigable leaf in sidebar order
 * - Fallback: `/executions` (core Workflow search)
 */

import { MenuItemType } from "components/providers/sidebar/types";
import { getCoreSidebarItems } from "components/providers/sidebar/sidebarCoreItems";
import { mergePluginSidebarItems } from "components/providers/sidebar/sidebarMenuUtils";
import { pluginRegistry } from "plugins/registry";
import { FEATURES, featureFlags } from "utils";

const FALLBACK_HOME = "/executions";

function isExternalOrBlank(linkTo: string | undefined): boolean {
  if (!linkTo || linkTo === "") return true;
  if (linkTo.startsWith("http://") || linkTo.startsWith("https://")) {
    return true;
  }
  if (linkTo.startsWith("//")) return true;
  if (linkTo.startsWith("mailto:")) return true;
  return false;
}

/**
 * Depth-first walk of sorted menu items; returns the first internal app path.
 * Skips hidden items, external / new-tab links, component-only entries, and `/`
 * (avoids a self-redirect loop when Hub points at `/`).
 */
export function findFirstNavigableSidebarPath(
  items: MenuItemType[],
): string | undefined {
  for (const item of items) {
    if (item.hidden) continue;

    const children = item.items?.filter((c) => !c.hidden) ?? [];
    if (children.length > 0) {
      const nested = findFirstNavigableSidebarPath(children);
      if (nested) return nested;
      continue;
    }

    // Component-only controls (e.g. agent toggle, run-workflow button)
    if (item.component && isExternalOrBlank(item.linkTo)) continue;
    if (item.isOpenNewTab) continue;
    if (isExternalOrBlank(item.linkTo)) continue;
    if (item.linkTo === "/" || item.linkTo === "") continue;

    return item.linkTo;
  }
  return undefined;
}

/**
 * Build the merged sidebar (same as UiSidebar) and pick the default home path.
 */
export function resolveDefaultHomePath(): string {
  if (featureFlags.isEnabled(FEATURES.PLAYGROUND)) {
    return "/";
  }

  const menu = mergePluginSidebarItems(
    getCoreSidebarItems(false),
    pluginRegistry.getSidebarItems(),
  );
  return findFirstNavigableSidebarPath(menu) ?? FALLBACK_HOME;
}
