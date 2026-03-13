export const GET_STARTED_URL = "/get-started";
export const HUB_URL = "/hub";
export const WEBHOOK_ROUTE_URL = {
  LIST: "/configure-webhooks",
  ID: "/configure-webhooks/:id",
  NEW: "/newWebhook",
};

export const METADATA_MIGRATION_ENVIRONMENT_URL = {
  BASE: "/environment",
  LIST: "/environments",
  ID: "/environments/:id",
  NEW: "/newEnvironment",
};

export const METADATA_MIGRATION_REQUEST_URL = {
  BASE: "/migrationRequest",
};

export const NEW_TASK_DEF_URL = "/newTaskDef";
export const TASK_DEF_URL = {
  BASE: "/taskDef",
  NAME: "/taskDef/:name",
};

export const WORKFLOW_DEFINITION_URL = {
  BASE: "/workflowDef",
  NAME_VERSION: "/workflowDef/:name/:version?",
  NEW: "/newWorkflowDef",
};

export const SCHEDULER_DEFINITION_URL = {
  BASE: "/scheduleDef",
  NAME: "/scheduleDef/:name?",
  NEW: "/newScheduleDef",
};

export const SCHEDULER_EXECUTION_URL = "/schedulerExecs";

export const USER_MANAGEMENT_URL = {
  BASE: "/userManagement",
  TYPE_ID: "/userManagement/:type?/:id?",
  LIST: "/userManagement/users",
  EDIT: "/userManagement/users/:id",
};

export const INTEGRATIONS_MANAGEMENT_URL = {
  BASE: "/integrations",
  ADD: "/integrations/addIntegration",
  EDIT: "/integrations/:id/integration",
  EDIT_INTEGRATION_MODEL: "/integrations/:id/configuration",
  EDIT_AI_MODEL: "/integrations/:id?/integration/:aiModelId",
};

export const AI_PROMPTS_MANAGEMENT_URL = {
  BASE: "/ai_prompts",
  NEW_AI_PROMPT_MODEL: "/ai_prompts/new_ai_prompt_model",
  EDIT: "/ai_prompts/:id/:version?",
};

export const GROUP_MANAGEMENT_URL = {
  BASE: "/groupManagement",
  TYPE_ID: "/groupManagement/:type?/:id?",
  LIST: "/groupManagement/groups",
  EDIT: "/groupManagement/groups/:id",
};

export const APPLICATION_MANAGEMENT_URL = {
  BASE: "/applicationManagement",
  TYPE_ID: "/applicationManagement/:type?/:id?",
  LIST: "/applicationManagement/applications",
  EDIT: "/applicationManagement/applications/:id",
};

export const ROLE_MANAGEMENT_URL = {
  BASE: "/roleManagement",
  TYPE_ID: "/roleManagement/:type?/:id?",
  LIST: "/roleManagement/roles",
  EDIT: "/roleManagement/roles/:id",
};

export const EVENT_HANDLERS_URL = {
  BASE: "/eventHandlerDef",
  NAME: "/eventHandlerDef/:name",
  NEW: "/newEventHandlerDef",
};

export const TASK_QUEUE_URL = {
  BASE: "/taskQueue",
  NAME: "/taskQueue/:name?",
};

export const SECRETS_URL = {
  BASE: "/secrets",
};

export const RUN_WORKFLOW_URL = "/runWorkflow";

export const HUMAN_TASK_URL = {
  BASE: "/human",
  LIST: "/human/tasks",
  TEMPLATES: "/human/templates",
  TEMPLATES_NAME_VERSION: "/human/templates/:templateName/:version?",
  TASK: "/human/task",
  TASK_ID: "/human/task/:taskId?",
  TASK_INBOX: "/human/task-inbox",
};

export const SCHEMAS_URL = {
  BASE: "/schemas",
  EDIT: "/schemas/:schemaName/:version?",
  DEF: "/schemas/schemaDef",
};

export const REMOTE_SERVICES_URL = {
  BASE: "/remote-services",
  EDIT: "/remote-services/:serviceName",
  NEW: "/newRemoteServiceDef",
};

export const SERVICE_URL = {
  LIST: "/services",
  EDIT: "/services/edit/:serviceId",
  NEW: "/newService",
  SERVICE_ID: "/services/:serviceId",
  SWAGGER: "/services/:serviceId/swagger",
  ROUTE_DETAILS: "/services/:serviceId/routes/*",
  ROUTE_EDIT: "/services/:serviceId/edit/routes/*",
  NEW_ROUTE: "/services/:serviceId/routes/new",
};

export const AUTHENTICATION_URL = "/authentication";

export const ERROR_URL = "/error";

export const WORKFLOW_EXPLORER_URL = "/workflowExplorer";

export const OIDC_CALLBACK_ROUTE = "/login/oidc/callback";

export const WORKFLOW_EXECUTION_URL = {
  BASE: "/execution",
  WF_ID_TASK_ID: "/execution/:id/:taskId?",
};

export const TASK_EXECUTION_URL = {
  LIST: "/taskExecs",
};

export const ENV_VARIABLES_URL = {
  BASE: "/environment",
};

export const EVENT_MONITOR_URL = {
  BASE: "/eventMonitor",
  NAME: "/eventMonitor/:name",
};

export const WORKERS_URL = {
  BASE: "/workers",
};

export const TAGS_DASHBOARD_URL = {
  BASE: "/tags-dashboard",
};

export const API_REFERENCE_URL = {
  BASE: "/api-reference",
};
