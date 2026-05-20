/**
 * Plugin Registry Types
 *
 * This module defines the interfaces for the Conductor UI plugin system.
 * Plugins can extend the application with:
 * - Routes (authenticated and public)
 * - Sidebar menu items
 * - Task forms for the workflow editor
 * - Task menu items for the "Add Task" menu
 * - Authentication providers
 * - Search providers for global search
 * - Sidebar state machine extensions
 * - Task documentation URLs
 */

import { ComponentType, ReactNode } from "react";
import { RouteObject } from "react-router-dom";
import { CSSObject } from "@mui/material/styles";
import { AuthHeaders } from "types/common";
import { BaseIntegration, IntegrationDef } from "types/Integrations";

// ============================================================================
// Task Form Types
// ============================================================================

/**
 * Props passed to task form components in the workflow editor
 */
export interface PluginTaskFormProps {
  task: Record<string, any>;
  onChange: (task: Record<string, any>) => void;
  updateAdditionalFieldMetadata?: any;
  additionalFieldMetadata?: any;
  isMetaBarEditing?: boolean;
  onToggleExpand?: (workflowName: string) => void;
  collapseWorkflowList?: string[];
  taskFormHeaderActor?: any; // ActorRef from xstate
}

/**
 * Registration for a task form component
 */
export interface TaskFormRegistration {
  /** The task type (e.g., "HUMAN", "WAIT_FOR_WEBHOOK") */
  taskType: string;
  /** The React component to render for this task type */
  component: ComponentType<PluginTaskFormProps>;
}

// ============================================================================
// Task Menu Types
// ============================================================================

/**
 * Category tabs in the "Add Task" menu
 */
export type TaskMenuCategory =
  | "ALL"
  | "System"
  | "Operators"
  | "Alerting"
  | "Workers"
  | "AI Tasks"
  | "Integrations";

/**
 * Registration for a task type in the "Add Task" menu
 */
export interface TaskMenuItemRegistration {
  /** Display name in the menu */
  name: string;
  /** Description shown below the name */
  description: string;
  /** The task type constant (e.g., "HUMAN", "WAIT_FOR_WEBHOOK") */
  type: string;
  /** Category tab where this task appears */
  category: TaskMenuCategory;
  /** Optional version number */
  version?: number;
  /** If true, this item is hidden (can be used with feature flags) */
  hidden?: boolean;
  /**
   * If true, this task type appears in the QuickAdd grid of the workflow editor.
   * Enterprise plugins use this to surface their task types in the quick-add panel.
   */
  quickAdd?: boolean;
}

// ============================================================================
// Sidebar Types
// ============================================================================

/**
 * Target submenu where a sidebar item should be inserted
 */
export type SidebarMenuTarget =
  | "executionsSubMenu"
  | "definitionsSubMenu"
  | "integrationsSubMenu"
  | "apiSubMenu"
  | "adminSubMenu"
  | "helpMenu"
  | "root"; // For top-level items

/**
 * Position hint for where to insert the item within the target menu
 */
export type SidebarItemPosition = "start" | "end" | number;

/**
 * Registration for a sidebar menu item
 */
export interface SidebarItemRegistration {
  /** Unique identifier for this menu item */
  id: string;
  /** Display title */
  title: string;
  /** Icon element (can be null for submenu items) */
  icon: ReactNode;
  /** Route path to navigate to (empty string for submenu parents) */
  linkTo: string;
  /** Additional routes that should mark this item as active */
  activeRoutes?: string[];
  /** Keyboard shortcuts */
  shortcuts?: string[];
  /** Hotkey string */
  hotkeys?: string;
  /** Target submenu to insert into */
  targetMenu: SidebarMenuTarget;
  /** Position within the target menu */
  position?: SidebarItemPosition;
  /** Whether this item is hidden */
  hidden?: boolean;
  /** Open link in new tab */
  isOpenNewTab?: boolean;
  /** Custom text styles */
  textStyle?: CSSObject;
  /** Custom button container styles */
  buttonContainerStyle?: CSSObject;
  /** Custom icon container styles */
  iconContainerStyles?: CSSObject;
  /** Click handler (instead of navigation) */
  handler?: () => void;
  /** Custom component to render instead of default */
  component?: ReactNode;
  /** Nested submenu items (for creating new submenus) */
  items?: SidebarItemRegistration[];
  /**
   * Optional React hook that returns the current badge count for this item.
   * When the returned value is > 0, a red badge with the count is shown next
   * to the item title. Enterprise plugins use this to show pending task counts.
   *
   * Must follow React hook rules (called unconditionally in component render).
   */
  useBadgeCount?: () => number;
}

// ============================================================================
// Auth Provider Types
// ============================================================================

/**
 * Props for auth provider wrapper components
 */
export interface AuthProviderProps {
  children: ReactNode;
}

/**
 * Registration for an authentication provider
 */
export interface AuthProviderRegistration {
  /** Provider type identifier (e.g., "auth0", "okta", "oidc") */
  type: string;
  /** The provider component that wraps the app */
  component: ComponentType<AuthProviderProps>;
}

// ============================================================================
// Search Provider Types
// ============================================================================

/**
 * A search result item returned by search providers
 */
export interface SearchResultItem {
  /** Icon to display */
  icon?: ReactNode;
  /** Display title */
  title: string;
  /** Route to navigate to when clicked */
  route?: string;
  /** Nested results (for grouped results) */
  sub?: SearchResultItem[];
}

/**
 * Function type for fetching search data
 */
export type SearchDataFetcher = (authHeaders?: AuthHeaders) => Promise<any[]>;

/**
 * Function type for transforming fetched data into search results
 */
export type SearchResultMapper = (
  data: any[],
  searchTerm: string,
) => SearchResultItem[];

/**
 * Registration for a search provider
 */
export interface SearchProviderRegistration {
  /** Unique identifier for this search provider */
  id: string;
  /** Human-readable name for the search category */
  name: string;
  /** Function to fetch the searchable data */
  fetcher: SearchDataFetcher;
  /** Function to map data to search results */
  mapper: SearchResultMapper;
  /** Priority for ordering results (lower = higher priority) */
  priority?: number;
}

// ============================================================================
// Sidebar Extension Types
// ============================================================================

/**
 * Extension for the sidebar state machine (e.g., for polling human tasks)
 */
export interface SidebarExtension {
  /** Unique identifier */
  id: string;
  /**
   * Initial context values to merge into sidebar machine context
   */
  initialContext?: Record<string, any>;
  /**
   * Service function to invoke (e.g., for polling)
   * Returns data that will be passed to the onDone handler
   */
  service?: (context: any) => Promise<any>;
  /**
   * Interval in milliseconds for polling (if applicable)
   */
  pollingInterval?: number;
  /**
   * Action to run when service completes
   */
  onServiceDone?: (context: any, data: any) => Record<string, any>;
}

// ============================================================================
// Task Doc URL Types
// ============================================================================

/**
 * Registration for task documentation URLs
 */
export interface TaskDocUrlRegistration {
  /** The task type */
  taskType: string;
  /** The documentation URL */
  url: string;
}

// ============================================================================
// New Integration Modal Types
// ============================================================================

/**
 * Props for the "Create New Integration" modal component registered by
 * the enterprise integrations plugin.
 */
export interface NewIntegrationModalProps {
  integrationDefList: IntegrationDef[];
  integrationToEdit: Partial<BaseIntegration>;
  onClose: () => void;
  onAfterSave?: (savedIntegration: BaseIntegration) => void;
  nameEditable?: boolean;
  isNewIntegration?: boolean;
}

// ============================================================================
// Schema Dialog Types (for SchemaForm in workflow editor)
// ============================================================================

/**
 * Props for the SchemaEditDialog component.
 */
export interface SchemaEditDialogProps {
  initialData: {
    schemaName: string;
    schemaVersion?: string;
    isNewSchema: boolean;
  };
  open?: boolean;
  onClose?: (schema?: { name: string; version?: number }) => void;
}

/**
 * Props for the SchemaPreviewDialog component.
 */
export interface SchemaPreviewDialogProps {
  schemaName: string;
  schemaVersion?: number;
  open?: boolean;
  onClose?: () => void;
}

// ============================================================================
// Generated Key Dialog Types (for WorkflowPropertiesForm)
// ============================================================================

/**
 * Props for the GeneratedKeyDialog component used in MetadataBanner.
 */
export interface GeneratedKeyDialogProps {
  handleClose: () => void;
  applicationAccessKey: { id: string; secret: string };
  setIsToastOpen: (open: boolean) => void;
}

// ============================================================================
// Dependency Section Types (for DependenciesTab in workflow editor)
// ============================================================================

/**
 * Dependencies extracted from a workflow definition.
 * This matches the return type of scanTasksForDependenciesInWorkflow.
 */
export interface WorkflowDependencies {
  integrationNames: string[];
  promptNames: string[];
  userFormsNameVersion: Array<{ name: string; version?: string }>;
  schemas: Array<{ name: string; version?: string }>;
  secrets: string[];
  env: string[];
  workflowName?: string;
  workflowVersion?: number;
}

/**
 * Props passed to dependency section components in the workflow editor's Dependencies tab.
 */
export interface DependencySectionProps {
  /** All extracted workflow dependencies */
  dependencies: WorkflowDependencies;
}

/**
 * Registration for a dependency section in the workflow editor's Dependencies tab.
 */
export interface DependencySectionRegistration {
  /** Unique identifier for this section */
  id: string;
  /** Title displayed in the collapsible section header */
  title: string;
  /** Order in which sections appear (lower = first) */
  order: number;
  /** React component that renders the section content */
  component: ComponentType<DependencySectionProps>;
}

// ============================================================================
// Main Plugin Interface
// ============================================================================

/**
 * A Conductor UI plugin that can extend the application
 */
export interface ConductorPlugin {
  /**
   * Unique identifier for the plugin
   */
  id: string;

  /**
   * Human-readable name
   */
  name: string;

  /**
   * Plugin version
   */
  version?: string;

  /**
   * Routes to add inside the AuthGuard (authenticated routes)
   */
  routes?: RouteObject[];

  /**
   * Routes to add outside the AuthGuard (public routes like login callbacks)
   */
  publicRoutes?: RouteObject[];

  /**
   * Sidebar menu items to add
   */
  sidebarItems?: SidebarItemRegistration[];

  /**
   * Task form components to register
   */
  taskForms?: TaskFormRegistration[];

  /**
   * Task menu items for the "Add Task" menu
   */
  taskMenuItems?: TaskMenuItemRegistration[];

  /**
   * Authentication providers
   */
  authProviders?: AuthProviderRegistration[];

  /**
   * Search providers for global search
   */
  searchProviders?: SearchProviderRegistration[];

  /**
   * Sidebar state machine extensions
   */
  sidebarExtensions?: SidebarExtension[];

  /**
   * Task documentation URLs
   */
  taskDocUrls?: TaskDocUrlRegistration[];

  /**
   * Global React components to mount inside the authenticated app layout.
   * Use this for invisible side-effect components such as pollers.
   * Components receive no props and must be self-contained.
   */
  globalComponents?: ComponentType[];

  /**
   * A component that renders the "Create New Integration" modal used in the
   * RichAddTaskMenu Integrations tab. The component receives the base
   * integration template and callbacks via props injected by AddTaskSidebar.
   * Enterprise plugins use this to supply the IntegrationEditModel.
   */
  newIntegrationModal?: ComponentType<NewIntegrationModalProps>;

  /**
   * The page component rendered at the root path "/" in playground mode.
   * Enterprise plugins (e.g. the hub/playground plugin) register this to
   * show the template showcase. When not registered, the normal app is shown.
   */
  playgroundHomePage?: ComponentType;

  /**
   * Replacement layout component for the main app shell (sidebar + top bar).
   * Enterprise plugins register an agent-aware layout here; OSS uses BaseLayout.
   * Must accept `{ children: ReactNode }`.
   */
  appLayout?: ComponentType<{ children: ReactNode }>;

  /**
   * Sections to add to the Dependencies tab in the workflow editor.
   * Enterprise plugins register sections for integrations, prompts, secrets, etc.
   */
  dependencySections?: DependencySectionRegistration[];

  /**
   * Schema edit dialog component for inline schema editing in task forms.
   * Enterprise plugins register this; OSS builds hide edit buttons when null.
   */
  schemaEditDialog?: ComponentType<SchemaEditDialogProps>;

  /**
   * Schema preview dialog component for previewing schemas in task forms.
   * Enterprise plugins register this; OSS builds hide preview buttons when null.
   */
  schemaPreviewDialog?: ComponentType<SchemaPreviewDialogProps>;

  /**
   * Generated key dialog component for displaying access keys in MetadataBanner.
   * Enterprise plugins register this; OSS builds use a simple fallback.
   */
  generatedKeyDialog?: ComponentType<GeneratedKeyDialogProps>;

  /**
   * Login page component rendered when user is not authenticated.
   * Enterprise plugins register this; OSS builds show a simple fallback message.
   */
  loginPage?: ComponentType;

  /**
   * Auth guard component that wraps authenticated routes.
   * Enterprise plugins register this to enforce authentication.
   * OSS builds use a simple layout wrapper with no auth checks.
   * Must render <Outlet /> for child routes.
   */
  authGuard?: ComponentType<{ fallback?: ReactNode; runWorkflow?: boolean }>;

  /**
   * Function to get the current access token for API requests.
   * Enterprise plugins register this to provide JWT tokens from their auth provider.
   * OSS builds return null (no authentication).
   */
  getAccessToken?: () => string | null;

  /**
   * Initialization function called when plugin is registered
   */
  onRegister?: () => void;
}

// ============================================================================
// Registry Interface
// ============================================================================

/**
 * The plugin registry interface
 */
export interface PluginRegistry {
  /**
   * Register a plugin
   */
  register(plugin: ConductorPlugin): void;

  /**
   * Get all registered plugins
   */
  getPlugins(): ConductorPlugin[];

  /**
   * Get all authenticated routes from plugins
   */
  getRoutes(): RouteObject[];

  /**
   * Get all public routes from plugins
   */
  getPublicRoutes(): RouteObject[];

  /**
   * Get all sidebar items from plugins
   */
  getSidebarItems(): SidebarItemRegistration[];

  /**
   * Get a task form component for a given task type
   */
  getTaskForm(taskType: string): ComponentType<PluginTaskFormProps> | null;

  /**
   * Get all task menu items from plugins
   */
  getTaskMenuItems(): TaskMenuItemRegistration[];

  /**
   * Get an auth provider component for a given type
   */
  getAuthProvider(type: string): ComponentType<AuthProviderProps> | null;

  /**
   * Get all search providers from plugins
   */
  getSearchProviders(): SearchProviderRegistration[];

  /**
   * Get all sidebar extensions from plugins
   */
  getSidebarExtensions(): SidebarExtension[];

  /**
   * Get a task documentation URL for a given task type
   */
  getTaskDocUrl(taskType: string): string | null;

  /**
   * Get all task documentation URLs from plugins
   */
  getTaskDocUrls(): Record<string, string>;

  /**
   * Get all global components from plugins
   */
  getGlobalComponents(): ComponentType[];

  /**
   * Get the "Create New Integration" modal component from the integrations plugin.
   * Returns null in OSS builds (no integrations plugin registered).
   */
  getNewIntegrationModal(): ComponentType<NewIntegrationModalProps> | null;

  /**
   * Get the playground home page component.
   * Returns null when not registered (OSS or non-playground builds).
   */
  getPlaygroundHomePage(): ComponentType | null;

  /**
   * Get the app layout component (sidebar + top bar shell).
   * Returns null when not registered; callers should fall back to BaseLayout.
   */
  getAppLayout(): ComponentType<{ children: ReactNode }> | null;

  /**
   * Get all dependency sections for the workflow editor's Dependencies tab.
   * Returns sections sorted by order.
   */
  getDependencySections(): DependencySectionRegistration[];

  /**
   * Get the schema edit dialog component.
   * Returns null in OSS builds (no schema plugin registered).
   */
  getSchemaEditDialog(): ComponentType<SchemaEditDialogProps> | null;

  /**
   * Get the schema preview dialog component.
   * Returns null in OSS builds (no schema plugin registered).
   */
  getSchemaPreviewDialog(): ComponentType<SchemaPreviewDialogProps> | null;

  /**
   * Get the generated key dialog component.
   * Returns null in OSS builds (no access plugin registered).
   */
  getGeneratedKeyDialog(): ComponentType<GeneratedKeyDialogProps> | null;

  /**
   * Get the login page component.
   * Returns null in OSS builds (no auth plugin registered).
   */
  getLoginPage(): ComponentType | null;

  /**
   * Get the auth guard component.
   * Returns null in OSS builds (no auth plugin registered).
   * When null, routes.tsx uses the default OSS AuthGuard (layout wrapper only).
   */
  getAuthGuard(): ComponentType<{
    fallback?: ReactNode;
    runWorkflow?: boolean;
  }> | null;

  /**
   * Get the access token for API requests.
   * Returns null in OSS builds (no authentication).
   * Enterprise plugins provide the JWT token from their auth provider.
   */
  getAccessToken(): string | null;
}
