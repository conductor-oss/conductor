/**
 * Typed REST client for the Conductor API.
 *
 * Used by integration test files to create and clean up test data directly
 * against the backend (bypassing the UI) so tests stay focused on the
 * behaviour being verified rather than on setup navigation.
 *
 * All requests go directly to the Conductor server, not through the Vite
 * proxy, so this can be called from Node.js test hooks as well as from
 * browser page.evaluate() calls.
 */

const BASE =
  (process.env.CONDUCTOR_SERVER_URL ?? "http://localhost:8000") + "/api";

// ── Types ─────────────────────────────────────────────────────────────────────

export interface TaskRef {
  name: string;
  taskReferenceName: string;
  type: string;
  inputParameters?: Record<string, unknown>;
}

export interface WorkflowDef {
  name: string;
  version?: number;
  description?: string;
  tasks: TaskRef[];
  inputParameters?: string[];
  outputParameters?: Record<string, unknown>;
  timeoutSeconds?: number;
}

export interface TaskDef {
  name: string;
  description?: string;
  retryCount?: number;
  // Note: if timeoutSeconds is set, it must be greater than responseTimeoutSeconds
  // (which defaults to 3600). Omitting it avoids the validation constraint.
  timeoutSeconds?: number;
  responseTimeoutSeconds?: number;
  inputKeys?: string[];
  outputKeys?: string[];
}

export interface WorkflowSummary {
  workflowId: string;
  workflowType: string;
  status: string;
  startTime: string;
  endTime?: string;
}

export interface WorkflowTaskExecution {
  taskType: string;
  referenceTaskName: string;
  status: string;
  outputData?: Record<string, unknown>;
}

export interface WorkflowExecution {
  workflowId: string;
  status: string;
  workflowType: string;
  tasks?: WorkflowTaskExecution[];
}

export interface SearchResult<T> {
  totalHits: number;
  results: T[];
}

const TERMINAL_WORKFLOW_STATUSES = new Set([
  "COMPLETED",
  "FAILED",
  "TIMED_OUT",
  "TERMINATED",
]);

// ── Helpers ───────────────────────────────────────────────────────────────────

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: body ? { "Content-Type": "application/json" } : {},
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`${method} ${BASE}${path} → ${res.status}: ${text}`);
  }

  const text = await res.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

// ── Workflow definitions ───────────────────────────────────────────────────────

export async function createWorkflowDef(def: WorkflowDef): Promise<void> {
  await request<void>("POST", "/metadata/workflow", def);
}

export async function getWorkflowDefs(): Promise<WorkflowDef[]> {
  return request<WorkflowDef[]>("GET", "/metadata/workflow");
}

export async function getWorkflowDef(
  name: string,
  version = 1,
): Promise<WorkflowDef> {
  return request<WorkflowDef>(
    "GET",
    `/metadata/workflow/${name}?version=${version}`,
  );
}

export async function deleteWorkflowDef(
  name: string,
  version = 1,
): Promise<void> {
  await request<void>("DELETE", `/metadata/workflow/${name}/${version}`);
}

// ── Workflow executions ────────────────────────────────────────────────────────

/** Starts a workflow and returns the new workflow ID. */
export async function startWorkflow(
  name: string,
  input: Record<string, unknown> = {},
  version = 1,
): Promise<string> {
  // POST /api/workflow returns the workflow ID as plain text (not JSON).
  const res = await fetch(`${BASE}/workflow`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name, version, input }),
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`POST /api/workflow → ${res.status}: ${text}`);
  }
  return (await res.text()).trim();
}

export async function getWorkflowExecution(
  workflowId: string,
): Promise<WorkflowExecution> {
  return request("GET", `/workflow/${workflowId}`);
}

/** Polls until the workflow reaches a terminal status or the timeout elapses. */
export async function waitForWorkflow(
  workflowId: string,
  {
    timeoutMs = 60_000,
    pollMs = 1_000,
  }: { timeoutMs?: number; pollMs?: number } = {},
): Promise<WorkflowExecution> {
  const deadline = Date.now() + timeoutMs;
  let last: WorkflowExecution | undefined;
  while (Date.now() < deadline) {
    last = await getWorkflowExecution(workflowId);
    if (TERMINAL_WORKFLOW_STATUSES.has(last.status)) {
      return last;
    }
    await new Promise((r) => setTimeout(r, pollMs));
  }
  throw new Error(
    `Workflow ${workflowId} did not reach a terminal status within ${timeoutMs}ms` +
      (last ? ` (last status: ${last.status})` : ""),
  );
}

export async function searchWorkflows(params: {
  query?: string;
  freeText?: string;
  start?: number;
  size?: number;
}): Promise<SearchResult<WorkflowSummary>> {
  const qs = new URLSearchParams();
  if (params.query) qs.set("query", params.query);
  if (params.freeText) qs.set("freeText", params.freeText);
  if (params.start !== undefined) qs.set("start", String(params.start));
  if (params.size !== undefined) qs.set("size", String(params.size));
  return request("GET", `/workflow/search?${qs}`);
}

export async function terminateWorkflow(
  workflowId: string,
  reason = "e2e test cleanup",
): Promise<void> {
  await request<void>(
    "DELETE",
    `/workflow/${workflowId}?reason=${encodeURIComponent(reason)}`,
  );
}

// ── Task definitions ──────────────────────────────────────────────────────────

export async function createTaskDef(def: TaskDef): Promise<void> {
  // POST /api/metadata/taskdefs accepts an array.
  await request<void>("POST", "/metadata/taskdefs", [def]);
}

export async function getTaskDef(taskType: string): Promise<TaskDef> {
  return request<TaskDef>("GET", `/metadata/taskdefs/${taskType}`);
}

export async function deleteTaskDef(taskType: string): Promise<void> {
  await request<void>("DELETE", `/metadata/taskdefs/${taskType}`);
}

// ── Agents (AgentSpan — requires conductor.integrations.ai.enabled=true) ───────

export interface AgentSummary {
  name: string;
  version?: number;
}

export interface AgentDeployResponse {
  agentName: string;
}

/** Returns true when GET /api/agent/list is available (AI integrations enabled). */
export async function isAgentApiAvailable(): Promise<boolean> {
  try {
    const res = await fetch(`${BASE}/agent/list`, {
      signal: AbortSignal.timeout(5_000),
    });
    return res.ok;
  } catch {
    return false;
  }
}

export async function listAgents(): Promise<AgentSummary[]> {
  return request<AgentSummary[]>("GET", "/agent/list");
}

/** Nested agentConfig payload accepted by POST /api/agent/deploy. */
export interface AgentConfigPayload {
  name: string;
  model?: string;
  instructions?: string;
  maxTurns?: number;
  strategy?: string;
  synthesize?: boolean;
  agents?: AgentConfigPayload[];
}

/**
 * Compiles and registers an agent definition via POST /api/agent/deploy.
 * Does not start an execution.
 */
export async function deployAgent(
  agentName: string,
  options: {
    model?: string;
    instructions?: string;
    maxTurns?: number;
    strategy?: string;
    synthesize?: boolean;
    agents?: AgentConfigPayload[];
  } = {},
): Promise<AgentDeployResponse> {
  const agentConfig: AgentConfigPayload = {
    name: agentName,
    model: options.model ?? "openai/gpt-4o-mini",
    instructions:
      options.instructions ??
      "You are a concise test agent. Answer in one sentence.",
    maxTurns: options.maxTurns ?? 1,
  };
  if (options.strategy) agentConfig.strategy = options.strategy;
  if (options.synthesize !== undefined) {
    agentConfig.synthesize = options.synthesize;
  }
  if (options.agents) agentConfig.agents = options.agents;

  return request<AgentDeployResponse>("POST", "/agent/deploy", {
    agentConfig,
  });
}

export async function deleteAgent(
  agentName: string,
  version?: number,
): Promise<void> {
  const qs = version !== undefined ? `?version=${version}` : "";
  await request<void>("DELETE", `/agent/${encodeURIComponent(agentName)}${qs}`);
}

export interface AgentStartResponse {
  executionId: string;
  agentName?: string;
}

/** Starts a deployed agent via POST /api/agent/start. */
export async function startAgent(
  agentName: string,
  prompt: string,
  options: { version?: number } = {},
): Promise<AgentStartResponse> {
  const body: Record<string, unknown> = {
    name: agentName,
    prompt,
  };
  if (options.version !== undefined) body.version = options.version;
  return request<AgentStartResponse>("POST", "/agent/start", body);
}

export interface AgentStatus {
  executionId: string;
  status: string;
  isComplete: boolean;
  isRunning: boolean;
  isWaiting?: boolean;
  output?: Record<string, unknown> | null;
  reasonForIncompletion?: string;
}

const TERMINAL_AGENT_STATUSES = new Set([
  "COMPLETED",
  "FAILED",
  "TIMED_OUT",
  "TERMINATED",
]);

export async function getAgentStatus(
  executionId: string,
): Promise<AgentStatus> {
  return request<AgentStatus>(
    "GET",
    `/agent/${encodeURIComponent(executionId)}/status`,
  );
}

/** Polls GET /api/agent/{id}/status until terminal or timeout. */
export async function waitForAgentExecution(
  executionId: string,
  {
    timeoutMs = 180_000,
    pollMs = 2_000,
  }: { timeoutMs?: number; pollMs?: number } = {},
): Promise<AgentStatus> {
  const deadline = Date.now() + timeoutMs;
  let last: AgentStatus | undefined;
  while (Date.now() < deadline) {
    last = await getAgentStatus(executionId);
    if (last.isComplete || TERMINAL_AGENT_STATUSES.has(last.status)) {
      return last;
    }
    await new Promise((r) => setTimeout(r, pollMs));
  }
  throw new Error(
    `Agent execution ${executionId} did not reach a terminal status within ${timeoutMs}ms` +
      (last
        ? ` (last status: ${last.status}, reason: ${last.reasonForIncompletion ?? "n/a"})`
        : ""),
  );
}
