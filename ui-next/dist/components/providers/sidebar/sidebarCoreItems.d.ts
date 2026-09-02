/**
 * Core (OSS) sidebar menu items for Conductor UI.
 *
 * These items are merged with plugin-registered items in UiSidebar.
 * - Executions submenu (Workflow, Scheduler, Queue Monitor)
 * - Run Workflow button
 * - Definitions submenu (Workflow, Agents, Task, Event Handler, Scheduler)
 * - Help menu
 * - API Docs
 */
import { MenuItemType } from "components/providers/sidebar/types";
/**
 * Returns the core OSS sidebar menu items. Accepts `open` for the Run Workflow
 * button component which depends on sidebar open state.
 * Each item has a numeric position so plugins can inject between (e.g. 150 between 100 and 200).
 */
export declare function getCoreSidebarItems(open: boolean): MenuItemType[];
