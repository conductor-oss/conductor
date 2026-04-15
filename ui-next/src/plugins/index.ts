/**
 * Plugins Module
 *
 * This module provides the plugin system and extension points for Conductor UI.
 *
 * - Plugin Registry: Register plugins to extend routes, sidebar, task forms, etc.
 * - Fetch: Authenticated HTTP client
 * - Custom Type Renderers: Extension point for custom task type rendering
 */

// Plugin registry - the main extension system
export {
  pluginRegistry,
  registerPlugin,
  // Types
  type ConductorPlugin,
  type PluginRegistry,
  type PluginTaskFormProps,
  type TaskFormRegistration,
  type TaskMenuCategory,
  type TaskMenuItemRegistration,
  type SidebarMenuTarget,
  type SidebarItemPosition,
  type SidebarItemRegistration,
  type AuthProviderProps,
  type AuthProviderRegistration,
  type SearchResultItem,
  type SearchDataFetcher,
  type SearchResultMapper,
  type SearchProviderRegistration,
  type SidebarExtension,
  type TaskDocUrlRegistration,
} from "./registry";

// Fetch utilities
export {
  fetchWithContext,
  useFetchContext,
  fetchContextNonHook,
} from "./fetch";
