/**
 * UiSidebar - Main sidebar component for Conductor UI
 *
 * This component defines the core (OSS) sidebar menu items and merges in
 * any additional items registered by plugins (enterprise features).
 *
 * Core OSS items:
 * - Executions submenu (Workflow, Scheduler, Queue Monitor)
 * - Run Workflow button
 * - Definitions submenu (Workflow, Task, Event Handler, Scheduler)
 * - API Docs
 * - Help menu
 *
 * Enterprise items are registered via plugins and merged at runtime.
 */
import { FunctionComponent } from "react";
type UISidebarProps = {
    /** undefined = loading (skeleton), null = error/unavailable, string = loaded */
    apiVersion?: string | null;
    releaseVersion?: string;
};
export declare const UISidebar: FunctionComponent<UISidebarProps>;
export {};
