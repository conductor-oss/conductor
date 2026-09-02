/**
 * Plugin Registry
 *
 * This module provides the plugin system for Conductor UI.
 * Use registerPlugin() to add plugins that extend the application.
 *
 * @example
 * ```typescript
 * import { registerPlugin, ConductorPlugin } from 'plugins/registry';
 *
 * const myPlugin: ConductorPlugin = {
 *   id: 'my-plugin',
 *   name: 'My Plugin',
 *   routes: [...],
 *   sidebarItems: [...],
 *   taskForms: [...],
 * };
 *
 * registerPlugin(myPlugin);
 * ```
 */
export { pluginRegistry, registerPlugin } from "./registry";
export type { ConductorPlugin, PluginRegistry, PluginTaskFormProps, TaskFormRegistration, TaskMenuCategory, TaskMenuItemRegistration, SidebarMenuTarget, SidebarItemPosition, SidebarItemRegistration, AuthProviderProps, AuthProviderRegistration, SearchResultItem, SearchDataFetcher, SearchResultMapper, SearchProviderRegistration, SidebarExtension, TaskDocUrlRegistration, DependencySectionRegistration, DependencySectionProps, WorkflowDependencies, SchemaEditDialogProps, SchemaPreviewDialogProps, GeneratedKeyDialogProps, } from "./types";
