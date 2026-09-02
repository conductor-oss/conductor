/**
 * Plugin Registry Implementation
 *
 * Singleton registry that manages all registered plugins and provides
 * methods to access their contributed functionality.
 */
import { ConductorPlugin, PluginRegistry } from "./types";
/**
 * The global plugin registry singleton
 */
export declare const pluginRegistry: PluginRegistry;
/**
 * Convenience function to register a plugin
 */
export declare function registerPlugin(plugin: ConductorPlugin): void;
