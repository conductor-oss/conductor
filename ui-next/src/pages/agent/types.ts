/**
 * Wire types for the embedded Conductor-Agents REST API (conductor-agentspan).
 * Kept local to the agent pages; mirror the server DTOs.
 */

export interface AgentSummary {
  name: string;
  version: number;
  type?: string;
  /** Provider-specific connection target: Foundry project URL, Bedrock region, etc. */
  endpoint?: string;
  tags?: string[];
  createTime?: number;
  updateTime?: number;
  description?: string;
  checksum?: string;
  schemaVersion?: number;
  restartable?: boolean;
  workflowStatusListenerEnabled?: boolean;
  ownerEmail?: string;
  inputParameters?: string[];
  outputParameters?: Record<string, unknown>;
  timeoutPolicy?: string;
  timeoutSeconds?: number;
  failureWorkflow?: string;
}

export interface AgentExecutionSummary {
  executionId: string;
  agentName: string;
  version: number;
  status: string;
  startTime?: string;
  endTime?: string;
  updateTime?: string;
  executionTime?: number;
  input?: string;
  output?: string;
  createdBy?: string;
}

export interface AgentExecutionSearchResult {
  totalHits: number;
  results: AgentExecutionSummary[];
}

export interface SkillSummary {
  name: string;
  version: string;
  description?: string;
  checksum?: string;
  status?: string;
  ownerId?: string;
  createdAt?: number;
  updatedAt?: number;
  packageSize?: number;
  fileCount?: number;
  scriptCount?: number;
  subAgentCount?: number;
  resourceCount?: number;
}

export interface CredentialMeta {
  name: string;
  partial?: string;
  created_at?: string;
  updated_at?: string;
}
