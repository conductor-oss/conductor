/**
 * Conductor UI - Open Source
 *
 * This is the main entry point for the conductor-ui npm package.
 * It exports the plugin system, core components, pages, utilities, and types
 * that enterprise packages can use to extend the application.
 */

// =============================================================================
// Plugin System - Primary export for enterprise extensions
// =============================================================================
export { pluginRegistry } from "./plugins/registry";
export type {
  ConductorPlugin,
  PluginRegistry,
  // Task forms
  PluginTaskFormProps,
  TaskFormRegistration,
  // Task menu
  TaskMenuCategory,
  TaskMenuItemRegistration,
  // Sidebar
  SidebarItemRegistration,
  SidebarItemPosition,
  SidebarMenuTarget,
  SidebarExtension,
  // Auth
  AuthProviderProps,
  AuthProviderRegistration,
  // Search
  SearchProviderRegistration,
  SearchResultItem,
  SearchDataFetcher,
  SearchResultMapper,
  // Task docs
  TaskDocUrlRegistration,
  // Integration modal
  NewIntegrationModalProps,
  // Playground
  // PlaygroundHomeRegistration - not a type, handled differently
  // App layout
  // AppLayoutRegistration - not a type, handled differently
  // Dependencies
  DependencySectionProps,
  DependencySectionRegistration,
  WorkflowDependencies,
  // Schema dialogs
  SchemaEditDialogProps,
  SchemaPreviewDialogProps,
  // Generated key dialog
  GeneratedKeyDialogProps,
} from "./plugins/registry/types";

// =============================================================================
// App Shell & Routing
// =============================================================================
export { App } from "./components/App";
export { getRoutes } from "./routes/routes";
export { default as AuthGuard } from "./components/auth/AuthGuard";

// =============================================================================
// Core Components
// =============================================================================
export * from "./components";

// Additional commonly used components
export { default as Header } from "./components/Header";
export { default as ClipboardCopy } from "./components/ClipboardCopy";
export { default as ConfirmChoiceDialog } from "./components/ConfirmChoiceDialog";
export { default as NoDataComponent } from "./components/NoDataComponent";
export { DocLink } from "./components/DocLink";
export { SnackbarMessage } from "./components/SnackbarMessage";
export { TagsRenderer } from "./components/v1/TagList";
export { default as AddIcon } from "./components/v1/icons/AddIcon";
export { default as CopyIcon } from "./components/v1/icons/CopyIcon";
export { default as AddTagDialog } from "./components/tags/AddTagDialog";

// Sidebar components
export { Sidebar } from "./components/Sidebar";
export { SidebarContext } from "./components/Sidebar/context/SidebarContext";
export { SidebarProvider } from "./components/Sidebar/context/SidebarContextProvider";
export { getCoreSidebarItems } from "./components/Sidebar/sidebarCoreItems";

// =============================================================================
// Core Pages (for customization/extension)
// =============================================================================
export { WorkflowSearch, SchedulerExecutions } from "./pages/executions";
export { default as WorkflowDefinition } from "./pages/definition/WorkflowDefinition";
export { TaskDefinition } from "./pages/definition/task";
export { EventMonitor } from "./pages/eventMonitor/EventMonitor";
export { default as TaskQueue } from "./pages/queueMonitor/TaskQueue";
export { default as ErrorPage } from "./pages/error/ErrorPage";

// Definition pages
export {
  Workflow as WorkflowDefinitions,
  Task as TaskDefinitions,
  EventHandler as EventHandlerDefinitions,
  Schedules as ScheduleDefinitions,
} from "./pages/definitions";

// =============================================================================
// Shared Utilities & Hooks
// =============================================================================
export { useAuth } from "./shared/auth";
export { UISidebar } from "./components/Sidebar/UiSidebar";

// =============================================================================
// Auth Infrastructure (minimal stubs for OSS mode)
// Full auth implementation is in the enterprise package.
// =============================================================================
export { authProviderMachine } from "./shared/state/machine";
export { AuthContext } from "./shared/auth/context";
export type { AuthState } from "./shared/auth/types";
export { defaultAuthState } from "./shared/auth/types";
export {
  setTokenData,
  getTokenData,
  getAccessToken,
} from "./shared/auth/tokenManagerJotai";
export {
  SupportedProviders,
  AuthMachineEventTypes,
  AuthProviderStates,
} from "./shared/state/types";
export type {
  AuthProviderMachineContext,
  AuthProviderMachineEvents,
} from "./shared/state/types";

// =============================================================================
// Query Client (for data fetching)
// =============================================================================
export { queryClient } from "./queryClient";

// =============================================================================
// Plugin Fetch Utilities
// =============================================================================
export { fetchWithContext, fetchContextNonHook } from "./plugins/fetch";

// =============================================================================
// Feature Flags & Logger
// =============================================================================
export { featureFlags, FEATURES, logger } from "./utils";

// =============================================================================
// Theme Provider
// =============================================================================
export { Provider as ThemeProvider } from "./theme/material/provider";
export { MessageProvider } from "./components/v1/layout/MessageContext";

// =============================================================================
// Common Constants
// =============================================================================
export {
  HOT_KEYS_SIDEBAR,
  HOT_KEYS_WORKFLOW_DEFINITION,
} from "./utils/constants/common";

// =============================================================================
// Route Constants
// =============================================================================
export {
  API_REFERENCE_URL,
  EVENT_HANDLERS_URL,
  EVENT_MONITOR_URL,
  NEW_TASK_DEF_URL,
  RUN_WORKFLOW_URL,
  SCHEDULER_DEFINITION_URL,
  SCHEDULER_EXECUTION_URL,
  TAGS_DASHBOARD_URL,
  TASK_DEF_URL,
  TASK_QUEUE_URL,
  WORKFLOW_DEFINITION_URL,
  WORKFLOW_EXECUTION_URL,
  // Enterprise route constants (used by enterprise plugins)
  WEBHOOK_ROUTE_URL,
  USER_MANAGEMENT_URL,
  INTEGRATIONS_MANAGEMENT_URL,
  AI_PROMPTS_MANAGEMENT_URL,
  GROUP_MANAGEMENT_URL,
  APPLICATION_MANAGEMENT_URL,
  ROLE_MANAGEMENT_URL,
  SECRETS_URL,
  HUMAN_TASK_URL,
  SCHEMAS_URL,
  REMOTE_SERVICES_URL,
  SERVICE_URL,
  AUTHENTICATION_URL,
  ENV_VARIABLES_URL,
  WORKERS_URL,
  GET_STARTED_URL,
  HUB_URL,
} from "./utils/constants/route";

// =============================================================================
// Types
// =============================================================================
export * from "./types";
export type { TaskType } from "./types";
