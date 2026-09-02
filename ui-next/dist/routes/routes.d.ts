/**
 * Routes Configuration
 *
 * This module defines the application routes. Core routes are defined inline,
 * while enterprise routes are registered via the plugin system.
 *
 * Core routes (OSS):
 * - Workflow definitions and executions
 * - Task definitions
 * - Event handlers
 * - Scheduler definitions and executions
 * - Queue monitor
 * - Event monitor
 * - API reference
 * - Tags dashboard
 *
 * Enterprise routes (registered via plugins):
 * - Auth (login, callbacks, RBAC pages)
 * - Webhooks
 * - Human Tasks
 * - AI Prompts
 * - Secrets
 * - Integrations
 * - Gateway Services
 * - Remote Services
 * - Metrics
 * - Environment Variables
 * - Schemas
 * - Workers
 */
import { RouteObject } from "react-router-dom";
/**
 * Build the complete route configuration
 */
export declare const getRoutes: () => RouteObject[];
