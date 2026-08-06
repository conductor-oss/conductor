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
  /** SWITCH: named case → task list */
  decisionCases?: Record<string, TaskRef[]>;
  /** SWITCH: fallback branch */
  defaultCase?: TaskRef[];
  /** FORK_JOIN: parallel branches */
  forkTasks?: TaskRef[][];
  /** JOIN / FORK_JOIN: branch refs to wait on */
  joinOn?: string[];
  /** SWITCH / INLINE evaluator */
  evaluatorType?: string;
  /** SWITCH expression / INLINE script key (also used by DO_WHILE as loopCondition sibling) */
  expression?: string;
  /** DO_WHILE */
  loopCondition?: string;
  loopOver?: TaskRef[];
  /** SUB_WORKFLOW */
  subWorkflowParam?: { name: string; version?: number };
}

export interface WorkflowDef {
  name: string;
  version?: number;
  description?: string;
  tasks: TaskRef[];
  inputParameters?: string[];
  outputParameters?: Record<string, unknown>;
  timeoutSeconds?: number;
  ownerEmail?: string;
  schemaVersion?: number;
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
  /** Present on search hits; GET /workflow/{id} uses workflowName instead. */
  workflowType?: string;
  workflowName?: string;
  tasks?: WorkflowTaskExecution[];
  input?: Record<string, unknown>;
  output?: Record<string, unknown>;
  variables?: Record<string, unknown>;
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

// ── Event handlers ─────────────────────────────────────────────────────────────

export interface EventHandlerAction {
  action: string;
  expandInlineJSON?: boolean;
  complete_task?: {
    workflowId: string;
    taskRefName: string;
  };
  start_workflow?: {
    name: string;
    version?: string | number;
  };
  [key: string]: unknown;
}

export interface EventHandlerDef {
  name: string;
  event: string;
  condition?: string;
  actions: EventHandlerAction[];
  active?: boolean;
  description?: string;
  evaluatorType?: string;
}

export async function createEventHandler(def: EventHandlerDef): Promise<void> {
  await request<void>("POST", "/event", def);
}

export async function getEventHandlers(): Promise<EventHandlerDef[]> {
  return request<EventHandlerDef[]>("GET", "/event");
}

export async function deleteEventHandler(name: string): Promise<void> {
  await request<void>("DELETE", `/event/${encodeURIComponent(name)}`);
}

// ── Scheduler definitions & executions ─────────────────────────────────────────

export interface StartWorkflowRequest {
  name: string;
  version?: number;
  input?: Record<string, unknown>;
  correlationId?: string;
  taskToDomain?: Record<string, string>;
  priority?: number;
}

export interface WorkflowSchedule {
  name: string;
  cronExpression: string;
  runCatchupScheduleInstances?: boolean;
  paused?: boolean;
  pausedReason?: string;
  zoneId?: string;
  scheduleStartTime?: number;
  scheduleEndTime?: number;
  description?: string;
  startWorkflowRequest: StartWorkflowRequest;
  createTime?: number;
  updatedTime?: number;
  nextRunTime?: number;
}

export type SchedulerExecutionState = "POLLED" | "EXECUTED" | "FAILED";

export interface WorkflowScheduleExecution {
  executionId: string;
  scheduleName: string;
  scheduledTime: number;
  executionTime: number;
  workflowName: string;
  workflowId?: string;
  state: SchedulerExecutionState;
  reason?: string;
  stackTrace?: string;
  zoneId?: string;
}

export async function createSchedule(
  schedule: WorkflowSchedule,
): Promise<WorkflowSchedule> {
  return request<WorkflowSchedule>("POST", "/scheduler/schedules", schedule);
}

export async function getSchedule(name: string): Promise<WorkflowSchedule> {
  return request<WorkflowSchedule>(
    "GET",
    `/scheduler/schedules/${encodeURIComponent(name)}`,
  );
}

export async function deleteSchedule(name: string): Promise<void> {
  await request<void>(
    "DELETE",
    `/scheduler/schedules/${encodeURIComponent(name)}`,
  );
}

export async function pauseSchedule(
  name: string,
  reason = "e2e test pause",
): Promise<void> {
  await request<void>(
    "PUT",
    `/scheduler/schedules/${encodeURIComponent(name)}/pause?reason=${encodeURIComponent(reason)}`,
  );
}

export async function resumeSchedule(name: string): Promise<void> {
  await request<void>(
    "PUT",
    `/scheduler/schedules/${encodeURIComponent(name)}/resume`,
  );
}

export async function searchSchedules(params: {
  scheduleName?: string;
  workflowName?: string;
  paused?: boolean;
  freeText?: string;
  start?: number;
  size?: number;
  sort?: string;
}): Promise<SearchResult<WorkflowSchedule>> {
  const qs = new URLSearchParams();
  if (params.scheduleName) qs.set("scheduleName", params.scheduleName);
  if (params.workflowName) qs.set("workflowName", params.workflowName);
  if (params.paused !== undefined) qs.set("paused", String(params.paused));
  if (params.freeText) qs.set("freeText", params.freeText);
  if (params.start !== undefined) qs.set("start", String(params.start));
  if (params.size !== undefined) qs.set("size", String(params.size));
  if (params.sort) qs.set("sort", params.sort);
  return request("GET", `/scheduler/schedules/search?${qs}`);
}

export async function searchSchedulerExecutions(params: {
  query?: string;
  freeText?: string;
  start?: number;
  size?: number;
  sort?: string;
}): Promise<SearchResult<WorkflowScheduleExecution>> {
  const qs = new URLSearchParams();
  if (params.query) qs.set("query", params.query);
  qs.set("freeText", params.freeText ?? "*");
  if (params.start !== undefined) qs.set("start", String(params.start));
  if (params.size !== undefined) qs.set("size", String(params.size));
  if (params.sort) qs.set("sort", params.sort);
  return request("GET", `/scheduler/search/executions?${qs}`);
}

/**
 * Polls until at least one scheduler execution for `scheduleName` reaches
 * `EXECUTED` (or another expected state), accounting for the ~15s scheduler
 * startup delay and archival lag in the default Docker config.
 */
export async function waitForSchedulerExecution(
  scheduleName: string,
  {
    timeoutMs = 120_000,
    pollMs = 2_000,
    state = "EXECUTED" as SchedulerExecutionState,
  }: {
    timeoutMs?: number;
    pollMs?: number;
    state?: SchedulerExecutionState;
  } = {},
): Promise<WorkflowScheduleExecution> {
  const deadline = Date.now() + timeoutMs;
  const query = `scheduleName IN (${scheduleName})`;
  let lastHits = 0;
  while (Date.now() < deadline) {
    const res = await searchSchedulerExecutions({
      query,
      start: 0,
      size: 10,
      sort: "scheduledTime:DESC",
    });
    lastHits = res.totalHits;
    const match = res.results?.find((r) => r.state === state);
    if (match) {
      return match;
    }
    await new Promise((r) => setTimeout(r, pollMs));
  }
  throw new Error(
    `No ${state} scheduler execution for ${scheduleName} within ${timeoutMs}ms` +
      ` (last totalHits=${lastHits})`,
  );
}
