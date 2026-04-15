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

// Export the registry singleton and convenience function
export { pluginRegistry, registerPlugin } from "./registry";

// Export all types
export type {
  // Main plugin interface
  ConductorPlugin,
  PluginRegistry,
  // Task form types
  PluginTaskFormProps,
  TaskFormRegistration,
  // Task menu types
  TaskMenuCategory,
  TaskMenuItemRegistration,
  // Sidebar types
  SidebarMenuTarget,
  SidebarItemPosition,
  SidebarItemRegistration,
  // Auth provider types
  AuthProviderProps,
  AuthProviderRegistration,
  // Search provider types
  SearchResultItem,
  SearchDataFetcher,
  SearchResultMapper,
  SearchProviderRegistration,
  // Sidebar extension types
  SidebarExtension,
  // Task doc URL types
  TaskDocUrlRegistration,
  // Dependency section types
  DependencySectionRegistration,
  DependencySectionProps,
  WorkflowDependencies,
  // Schema dialog types
  SchemaEditDialogProps,
  SchemaPreviewDialogProps,
  // Generated key dialog types
  GeneratedKeyDialogProps,
} from "./types";
