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

export interface SearchResult<T> {
  totalHits: number;
  results: T[];
}

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
  return res.text();
}

export async function getWorkflowExecution(
  workflowId: string,
): Promise<{ workflowId: string; status: string; workflowType: string }> {
  return request("GET", `/workflow/${workflowId}`);
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
