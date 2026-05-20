/**
 * Plugin Registry Implementation
 *
 * Singleton registry that manages all registered plugins and provides
 * methods to access their contributed functionality.
 */

import { ComponentType, ReactNode } from "react";
import { RouteObject } from "react-router-dom";
import {
  AuthProviderProps,
  ConductorPlugin,
  DependencySectionRegistration,
  GeneratedKeyDialogProps,
  NewIntegrationModalProps,
  PluginRegistry,
  PluginTaskFormProps,
  SchemaEditDialogProps,
  SchemaPreviewDialogProps,
  SearchProviderRegistration,
  SidebarExtension,
  SidebarItemRegistration,
  TaskMenuItemRegistration,
} from "./types";

/**
 * Creates a new plugin registry instance
 */
function createPluginRegistry(): PluginRegistry {
  // Storage for registered plugins
  const plugins: ConductorPlugin[] = [];

  // Cached lookups for performance
  const taskFormCache = new Map<string, ComponentType<PluginTaskFormProps>>();
  const authProviderCache = new Map<string, ComponentType<AuthProviderProps>>();
  const taskDocUrlCache = new Map<string, string>();

  // Flag to track if caches need rebuilding
  let cachesDirty = true;

  /**
   * Rebuild all caches from registered plugins
   */
  function rebuildCaches(): void {
    if (!cachesDirty) return;

    taskFormCache.clear();
    authProviderCache.clear();
    taskDocUrlCache.clear();

    for (const plugin of plugins) {
      // Cache task forms
      if (plugin.taskForms) {
        for (const registration of plugin.taskForms) {
          taskFormCache.set(registration.taskType, registration.component);
        }
      }

      // Cache auth providers
      if (plugin.authProviders) {
        for (const registration of plugin.authProviders) {
          authProviderCache.set(registration.type, registration.component);
        }
      }

      // Cache task doc URLs
      if (plugin.taskDocUrls) {
        for (const registration of plugin.taskDocUrls) {
          taskDocUrlCache.set(registration.taskType, registration.url);
        }
      }
    }

    cachesDirty = false;
  }

  return {
    /**
     * Register a plugin with the registry
     */
    register(plugin: ConductorPlugin): void {
      // Check for duplicate plugin IDs
      const existing = plugins.find((p) => p.id === plugin.id);
      if (existing) {
        console.warn(
          `Plugin with ID "${plugin.id}" is already registered. Skipping.`,
        );
        return;
      }

      plugins.push(plugin);
      cachesDirty = true;

      // Call plugin's onRegister hook if provided
      if (plugin.onRegister) {
        try {
          plugin.onRegister();
        } catch (error) {
          console.error(
            `Error in onRegister hook for plugin "${plugin.id}":`,
            error,
          );
        }
      }
    },

    /**
     * Get all registered plugins
     */
    getPlugins(): ConductorPlugin[] {
      return [...plugins];
    },

    /**
     * Get all authenticated routes from plugins
     */
    getRoutes(): RouteObject[] {
      const routes: RouteObject[] = [];
      for (const plugin of plugins) {
        if (plugin.routes) {
          routes.push(...plugin.routes);
        }
      }
      return routes;
    },

    /**
     * Get all public routes from plugins
     */
    getPublicRoutes(): RouteObject[] {
      const routes: RouteObject[] = [];
      for (const plugin of plugins) {
        if (plugin.publicRoutes) {
          routes.push(...plugin.publicRoutes);
        }
      }
      return routes;
    },

    /**
     * Get all sidebar items from plugins, sorted by position
     */
    getSidebarItems(): SidebarItemRegistration[] {
      const items: SidebarItemRegistration[] = [];
      for (const plugin of plugins) {
        if (plugin.sidebarItems) {
          items.push(...plugin.sidebarItems);
        }
      }
      return items;
    },

    /**
     * Get a task form component for a given task type
     */
    getTaskForm(taskType: string): ComponentType<PluginTaskFormProps> | null {
      rebuildCaches();
      return taskFormCache.get(taskType) || null;
    },

    /**
     * Get all task menu items from plugins
     */
    getTaskMenuItems(): TaskMenuItemRegistration[] {
      const items: TaskMenuItemRegistration[] = [];
      for (const plugin of plugins) {
        if (plugin.taskMenuItems) {
          // Filter out hidden items
          const visibleItems = plugin.taskMenuItems.filter(
            (item) => !item.hidden,
          );
          items.push(...visibleItems);
        }
      }
      return items;
    },

    /**
     * Get an auth provider component for a given type
     */
    getAuthProvider(type: string): ComponentType<AuthProviderProps> | null {
      rebuildCaches();
      return authProviderCache.get(type) || null;
    },

    /**
     * Get all search providers from plugins, sorted by priority
     */
    getSearchProviders(): SearchProviderRegistration[] {
      const providers: SearchProviderRegistration[] = [];
      for (const plugin of plugins) {
        if (plugin.searchProviders) {
          providers.push(...plugin.searchProviders);
        }
      }
      // Sort by priority (lower = higher priority)
      return providers.sort(
        (a, b) => (a.priority ?? 100) - (b.priority ?? 100),
      );
    },

    /**
     * Get all sidebar extensions from plugins
     */
    getSidebarExtensions(): SidebarExtension[] {
      const extensions: SidebarExtension[] = [];
      for (const plugin of plugins) {
        if (plugin.sidebarExtensions) {
          extensions.push(...plugin.sidebarExtensions);
        }
      }
      return extensions;
    },

    /**
     * Get a task documentation URL for a given task type
     */
    getTaskDocUrl(taskType: string): string | null {
      rebuildCaches();
      return taskDocUrlCache.get(taskType) || null;
    },

    /**
     * Get all task documentation URLs from plugins as a Record
     */
    getTaskDocUrls(): Record<string, string> {
      rebuildCaches();
      const urls: Record<string, string> = {};
      taskDocUrlCache.forEach((url, type) => {
        urls[type] = url;
      });
      return urls;
    },

    /**
     * Get all global components from plugins
     */
    getGlobalComponents(): ComponentType[] {
      const components: ComponentType[] = [];
      for (const plugin of plugins) {
        if (plugin.globalComponents) {
          components.push(...plugin.globalComponents);
        }
      }
      return components;
    },

    /**
     * Get the "Create New Integration" modal component.
     * Returns the first registered one, or null if none (OSS build).
     */
    getNewIntegrationModal(): ComponentType<NewIntegrationModalProps> | null {
      for (const plugin of plugins) {
        if (plugin.newIntegrationModal) {
          return plugin.newIntegrationModal;
        }
      }
      return null;
    },

    /**
     * Get the playground home page component.
     * Returns the first registered one, or null if none.
     */
    getPlaygroundHomePage(): ComponentType | null {
      for (const plugin of plugins) {
        if (plugin.playgroundHomePage) {
          return plugin.playgroundHomePage;
        }
      }
      return null;
    },

    /**
     * Get the app layout component.
     * Returns the first registered one, or null (use BaseLayout as fallback).
     */
    getAppLayout(): ComponentType<{ children: ReactNode }> | null {
      for (const plugin of plugins) {
        if (plugin.appLayout) {
          return plugin.appLayout;
        }
      }
      return null;
    },

    /**
     * Get all dependency sections for the workflow editor's Dependencies tab.
     * Returns sections sorted by order.
     */
    getDependencySections(): DependencySectionRegistration[] {
      const sections: DependencySectionRegistration[] = [];
      for (const plugin of plugins) {
        if (plugin.dependencySections) {
          sections.push(...plugin.dependencySections);
        }
      }
      // Sort by order (lower = first)
      return sections.sort((a, b) => a.order - b.order);
    },

    /**
     * Get the schema edit dialog component.
     * Returns the first registered one, or null if none (OSS build).
     */
    getSchemaEditDialog(): ComponentType<SchemaEditDialogProps> | null {
      for (const plugin of plugins) {
        if (plugin.schemaEditDialog) {
          return plugin.schemaEditDialog;
        }
      }
      return null;
    },

    /**
     * Get the schema preview dialog component.
     * Returns the first registered one, or null if none (OSS build).
     */
    getSchemaPreviewDialog(): ComponentType<SchemaPreviewDialogProps> | null {
      for (const plugin of plugins) {
        if (plugin.schemaPreviewDialog) {
          return plugin.schemaPreviewDialog;
        }
      }
      return null;
    },

    /**
     * Get the generated key dialog component.
     * Returns the first registered one, or null if none (OSS build).
     */
    getGeneratedKeyDialog(): ComponentType<GeneratedKeyDialogProps> | null {
      for (const plugin of plugins) {
        if (plugin.generatedKeyDialog) {
          return plugin.generatedKeyDialog;
        }
      }
      return null;
    },

    /**
     * Get the login page component.
     * Returns the first registered one, or null if none (OSS build).
     */
    getLoginPage(): ComponentType | null {
      for (const plugin of plugins) {
        if (plugin.loginPage) {
          return plugin.loginPage;
        }
      }
      return null;
    },

    /**
     * Get the auth guard component.
     * Returns the first registered one, or null if none (OSS build).
     */
    getAuthGuard(): ComponentType<{
      fallback?: ReactNode;
      runWorkflow?: boolean;
    }> | null {
      for (const plugin of plugins) {
        if (plugin.authGuard) {
          return plugin.authGuard;
        }
      }
      return null;
    },

    /**
     * Get the access token for API requests.
     * Returns null in OSS builds (no authentication).
     */
    getAccessToken(): string | null {
      for (const plugin of plugins) {
        if (plugin.getAccessToken) {
          return plugin.getAccessToken();
        }
      }
      return null;
    },
  };
}

/**
 * The global plugin registry singleton
 */
export const pluginRegistry = createPluginRegistry();

/**
 * Convenience function to register a plugin
 */
export function registerPlugin(plugin: ConductorPlugin): void {
  pluginRegistry.register(plugin);
}
