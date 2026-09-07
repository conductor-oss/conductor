export type AgentRuntimeType = "a2a" | "conductor";

export interface A2AAgentInterface {
  url?: string;
  protocolBinding?: string;
  protocolVersion?: string;
  tenant?: string;
  /** Legacy A2A 0.3 field. */
  transport?: string;
}

export interface A2AAgentExtension {
  uri?: string;
  description?: string;
  required?: boolean;
  params?: Record<string, unknown>;
}

export interface A2AAgentCapabilities {
  streaming?: boolean;
  pushNotifications?: boolean;
  stateTransitionHistory?: boolean;
  extendedAgentCard?: boolean;
  extensions?: A2AAgentExtension[];
}

export interface A2AAgentSkill {
  id?: string;
  name?: string;
  description?: string;
  tags?: string[];
  examples?: string[];
  inputModes?: string[];
  outputModes?: string[];
  securityRequirements?: Array<Record<string, unknown>>;
}

export interface A2AAgentCard {
  name?: string;
  description?: string;
  url?: string;
  version?: string;
  protocolVersion?: string;
  preferredTransport?: string;
  documentationUrl?: string;
  iconUrl?: string;
  provider?: { organization?: string; url?: string };
  capabilities?: A2AAgentCapabilities;
  skills?: A2AAgentSkill[];
  defaultInputModes?: string[];
  defaultOutputModes?: string[];
  supportedInterfaces?: A2AAgentInterface[];
  /** Legacy A2A 0.3 field. */
  additionalInterfaces?: A2AAgentInterface[];
  securitySchemes?: Record<string, unknown>;
  securityRequirements?: Array<Record<string, unknown>>;
  /** Legacy A2A 0.3 field. */
  security?: Array<Record<string, unknown>>;
  signatures?: Array<{
    protected?: string;
    signature?: string;
    header?: Record<string, unknown>;
  }>;
  /** Legacy pre-1.0 fields. */
  supportsAuthenticatedExtendedCard?: boolean;
  supportsExtendedAgentCard?: boolean;
}

export interface A2AAgentTaskInput {
  agentType?: "a2a";
  agentUrl: string;
  text?: string;
  message?: unknown;
  parts?: unknown;
  contextId?: string;
  taskId?: string;
  metadata?: unknown;
  pollIntervalSeconds?: number;
  maxDurationSeconds?: number;
  maxPollFailures?: number;
  historyLength?: number;
  streaming?: boolean;
  pushNotification?: boolean;
  pushBackstopPollSeconds?: number;
  headers?: Record<string, string>;
}

export interface ConductorAgentTaskInput {
  agentType: "conductor";
  name: string;
  version?: number;
  prompt?: string;
  model?: string;
  context?: Record<string, unknown>;
  executionId?: string;
  pollIntervalSeconds?: number;
  maxDurationSeconds?: number;
  maxPollFailures?: number;
}

export type AgentTaskInput = A2AAgentTaskInput | ConductorAgentTaskInput;

export interface AgentSnapshotSource {
  name?: string;
  requestedVersion?: number;
  url?: string;
  expression?: string;
}

export interface ConductorAgentSnapshot {
  name: string;
  requestedVersion?: number;
  resolvedVersion?: number;
  framework?: string;
  normalization?: "normalized" | "raw";
  agentConfig?: Record<string, unknown>;
}

export interface A2AAgentSnapshot {
  url: string;
  agentCard?: A2AAgentCard;
}

/** Versioned, descriptive snapshot persisted under WorkflowTask.metadata.agent. */
export interface AgentMetadataSnapshot {
  schemaVersion: 1;
  agentType: AgentRuntimeType;
  displayName: string;
  source: AgentSnapshotSource;
  resolved: boolean;
  conductor?: ConductorAgentSnapshot;
  a2a?: A2AAgentSnapshot;
}

export interface WorkflowTaskMetadata {
  agent?: AgentMetadataSnapshot;
  [key: string]: unknown;
}
