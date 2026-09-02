/**
 * Resolve the app "home" path from feature flags + the visible sidebar menu.
 *
 * - Playground: `/` (Launch Pad / Hub — registered as the public index)
 * - Otherwise: first in-app navigable leaf in sidebar order
 * - Fallback: `/executions` (core Workflow search)
 */
import { MenuItemType } from "components/providers/sidebar/types";
/**
 * Depth-first walk of sorted menu items; returns the first internal app path.
 * Skips hidden items, external / new-tab links, component-only entries, and `/`
 * (avoids a self-redirect loop when Hub points at `/`).
 */
export declare function findFirstNavigableSidebarPath(items: MenuItemType[]): string | undefined;
/**
 * Build the merged sidebar (same as UiSidebar) and pick the default home path.
 */
export declare function resolveDefaultHomePath(): string;
