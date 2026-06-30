// OSS Conductor UI Runtime Configuration
// This file configures feature flags at runtime for the OSS UI

window.conductor = {
  // Authentication - DISABLED for OSS
  ACCESS_MANAGEMENT: false,
  RBAC: false,
  COPY_TOKEN: false,

  // OSS Core Features
  SCHEDULER: true,
  TASK_VISIBILITY: "READ",
  CREATOR_ENABLE_CREATOR: true,
  CREATOR_ENABLE_REAFLOW_DIAGRAM: true,
  TASK_INDEXING: false,
  SHOW_EVENT_MONITOR: true,
  ENABLE_DARK_MODE_TOGGLE: true,

  // Enterprise Features - DISABLED for OSS
  TAG_VISIBILITY: false,
  WORKFLOW_INTROSPECTION: false,
  WORKFLOW_SUMMARIZE: false,
  HUMAN_TASK: false,
  INTEGRATIONS: false,
  SECRETS: false,
  WEBHOOKS: false,
  SERVICE_REGISTRY: false,
  GATEWAY_ENABLED: false,
  REMOTE_SERVICES: false,
  SENDGRID_TASK_ENABLED: false,
  SKU_ENABLED: false,

  // Embedded AgentSpan agent UI. Default off here; the Conductor server overrides
  // /context.js at runtime with the value of conductor.integrations.ai.enabled.
  AGENTSPAN_ENABLED: false,

  // UI Configuration
  PLAYGROUND: false,
  ENABLE_METRICS_DASHBOARD: false,
  METRICS_ORIGIN_URL: "",
  CUSTOM_LOGO_URL: "",
  MULTITENANCY_TYPE: "user_based",
  DEFAULT_ROLES: "ADMIN",
};

// No authentication configuration for OSS
window.authConfig = undefined;
window.auth0Identifiers = undefined;
