import {
  AgentEvent,
  AgentRunData,
  AgentStatus,
  AgentStrategy,
  AgentTurn,
  EventType,
  ExecutionMetrics,
  FinishReason,
  TaskAttempt,
  TokenUsage,
} from "./types";
import {
  ExecutionTask,
  WorkflowExecution,
  WorkflowExecutionStatus,
} from "types/Execution";

/** Map a model name to its provider icon path in /integrations-icons/ */
export function getModelIconPath(model: string | undefined): string | null {
  if (!model) return null;
  const m = model.toLowerCase();
  if (m.includes("claude")) return "/integrations-icons/anthropic.svg";
  if (
    m.includes("openai") ||
    m.includes("gpt") ||
    m.includes("o1") ||
    m.includes("o3")
  )
    return "/integrations-icons/openAI.svg";
  if (m.includes("gemini")) return "/integrations-icons/googlegemini.svg";
  if (m.includes("mistral")) return "/integrations-icons/mistralai.svg";
  if (m.includes("bedrock")) return "/integrations-icons/bedrock.svg";
  if (m.includes("azure")) return "/integrations-icons/azureOpenAI.svg";
  if (m.includes("cohere")) return "/integrations-icons/cohere.svg";
  if (m.includes("ollama") || m.includes("llama"))
    return "/integrations-icons/ollama.svg";
  if (m.includes("vertex")) return "/integrations-icons/vertexAI.svg";
  if (m.includes("perplexity")) return "/integrations-icons/perplexity.svg";
  if (m.includes("hugging") || m.startsWith("hf-"))
    return "/integrations-icons/huggingFace.svg";
  return null;
}

/** Build a CONTEXT_CONDENSED event from task inputData if _condensation metadata is present */
function maybeCondensationEvent(task: ExecutionTask): AgentEvent | null {
  const info = (task.inputData as Record<string, unknown> | undefined)
    ?._condensation;
  if (!info || typeof info !== "object") return null;
  const c = info as Record<string, unknown>;
  const condensationInfo = {
    trigger: String(c.trigger ?? ""),
    messagesBefore: Number(c.messagesBefore ?? 0),
    messagesAfter: Number(c.messagesAfter ?? 0),
    exchangesCondensed: Number(c.exchangesCondensed ?? 0),
  };
  return {
    id: `${task.taskId}-condensed`,
    type: EventType.CONTEXT_CONDENSED,
    timestamp: task.startTime ?? 0,
    summary: `Context condensed: ${condensationInfo.messagesBefore} → ${condensationInfo.messagesAfter} messages`,
    condensationInfo,
  };
}

function sumTokens(tokensList: TokenUsage[]): TokenUsage {
  return tokensList.reduce(
    (acc, t) => ({
      promptTokens: acc.promptTokens + t.promptTokens,
      completionTokens: acc.completionTokens + t.completionTokens,
      totalTokens: acc.totalTokens + t.totalTokens,
    }),
    { promptTokens: 0, completionTokens: 0, totalTokens: 0 },
  );
}

/** Compute aggregate metrics recursively across all agents */
export function computeMetrics(run: AgentRunData): ExecutionMetrics {
  const allRuns = collectAllRuns(run);
  const totalTokens = sumTokens(allRuns.map((r) => r.totalTokens));
  const totalTurns = allRuns.reduce((sum, r) => sum + r.turns.length, 0);
  const totalDurationMs = run.totalDurationMs; // Use root agent's wall-clock time
  const failedAgents = allRuns.filter(
    (r) => r.status === AgentStatus.FAILED,
  ).length;
  const waitingAgents = allRuns.filter(
    (r) => r.status === AgentStatus.WAITING,
  ).length;

  return {
    totalAgents: allRuns.length,
    totalTurns,
    totalTokens,
    totalDurationMs,
    failedAgents,
    waitingAgents,
  };
}

function collectAllRuns(run: AgentRunData): AgentRunData[] {
  const result: AgentRunData[] = [run];
  for (const turn of run.turns) {
    for (const sub of turn.subAgents) {
      result.push(...collectAllRuns(sub));
    }
  }
  return result;
}

/** Format duration in ms to a human-readable string */
export function formatDuration(ms: number): string {
  if (ms <= 0) return "—";
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

/** Format token count for display */
export function formatTokens(count: number): string {
  if (count < 1000) return String(count);
  return `${(count / 1000).toFixed(1)}k`;
}

// ─── Workflow Execution Transformers ────────────────────────────────────────

const ZERO_TOKENS: TokenUsage = {
  promptTokens: 0,
  completionTokens: 0,
  totalTokens: 0,
};

function toMs(value: string | number | undefined | null): number {
  if (value == null || value === 0 || value === "") return 0;
  return typeof value === "number" ? value : parseInt(value, 10) || 0;
}

/** Task statuses Conductor considers terminal failures (no further progress). */
const FAILED_TASK_STATUSES = new Set([
  "FAILED",
  "FAILED_WITH_TERMINAL_ERROR",
  "TIMED_OUT",
  "CANCELED",
]);

/** Task statuses that are still actively progressing (caller shows a spinner). */
const IN_PROGRESS_TASK_STATUSES = new Set([
  "IN_PROGRESS",
  "SCHEDULED",
  "PENDING",
]);

/**
 * Whether a task status represents a terminal failure. Use this instead of
 * comparing against the literal "FAILED" — Conductor has several terminal
 * failure statuses (FAILED_WITH_TERMINAL_ERROR, TIMED_OUT, CANCELED) and
 * treating only "FAILED" as failed leaves the others rendering as running.
 */
export function isFailedTaskStatus(status: string): boolean {
  return FAILED_TASK_STATUSES.has(status);
}

/** Maps task status to a tri-state success flag: true=completed, false=failed, undefined=in-progress */
export function taskSuccess(status: string): boolean | undefined {
  if (status === "COMPLETED") return true;
  if (FAILED_TASK_STATUSES.has(status)) return false;
  if (IN_PROGRESS_TASK_STATUSES.has(status)) return undefined; // caller shows spinner
  // Any other terminal status (SKIPPED, COMPLETED_WITH_ERRORS, NULL, or an
  // unknown future status) is treated as not-in-progress so the UI never shows
  // a perpetual spinner for a task the backend has already finished.
  return false;
}

/**
 * Deduplicate retried tasks: when Conductor retries a timed-out/failed task,
 * BOTH the original attempt AND the retry appear in the task list with the same
 * referenceTaskName but increasing retryCount. Keep only the latest attempt
 * (highest retryCount) and return a map of referenceTaskName → total attempts.
 */
function deduplicateRetriedTasks(tasks: ExecutionTask[]): {
  tasks: ExecutionTask[];
  attemptCounts: Map<string, number>;
  attemptGroups: Map<string, ExecutionTask[]>;
} {
  const byRef = new Map<string, ExecutionTask[]>();
  for (const t of tasks) {
    const ref = t.referenceTaskName;
    if (!byRef.has(ref)) byRef.set(ref, []);
    byRef.get(ref)!.push(t);
  }
  const deduped: ExecutionTask[] = [];
  const attemptCounts = new Map<string, number>();
  const attemptGroups = new Map<string, ExecutionTask[]>();
  for (const [ref, group] of byRef) {
    if (group.length > 1) {
      attemptCounts.set(ref, group.length);
      // Sort ascending by retryCount so attempt[0]=oldest, last=latest
      group.sort((a, b) => (a.retryCount ?? 0) - (b.retryCount ?? 0));
      attemptGroups.set(ref, group);
    }
    // Keep the latest attempt (last after ascending sort)
    deduped.push(group[group.length - 1]);
  }
  return { tasks: deduped, attemptCounts, attemptGroups };
}

function buildAllAttempts(
  refName: string,
  attemptGroups: Map<string, ExecutionTask[]>,
): TaskAttempt[] | undefined {
  const group = attemptGroups.get(refName);
  if (!group || group.length <= 1) return undefined;
  return group.map(
    (t): TaskAttempt => ({
      taskId: t.taskId ?? "",
      retryCount: t.retryCount ?? 0,
      status: t.status,
      startTime: t.startTime ?? undefined,
      endTime: t.endTime ?? undefined,
      durationMs: t.endTime && t.startTime ? t.endTime - t.startTime : 0,
      workerId: t.workerId ?? undefined,
      reasonForIncompletion: t.reasonForIncompletion ?? undefined,
      inputData: t.inputData as Record<string, unknown> | undefined,
      outputData: t.outputData as Record<string, unknown> | undefined,
    }),
  );
}

export function mapTaskStatus(status: string): AgentStatus {
  switch (status) {
    case "COMPLETED":
      return AgentStatus.COMPLETED;
    case "FAILED":
    case "FAILED_WITH_TERMINAL_ERROR":
    case "TIMED_OUT":
    case "CANCELED":
      return AgentStatus.FAILED;
    case "IN_PROGRESS":
    case "SCHEDULED":
    case "PENDING":
      return AgentStatus.RUNNING;
    default:
      // Unknown/other terminal statuses (e.g. SKIPPED, COMPLETED_WITH_ERRORS)
      // must not fall through to RUNNING — that renders a perpetual "running"
      // chip + spinner for a task the backend has already finished (issue #4260).
      return AgentStatus.FAILED;
  }
}

function mapWorkflowStatus(status: WorkflowExecutionStatus): AgentStatus {
  switch (status) {
    case WorkflowExecutionStatus.COMPLETED:
      return AgentStatus.COMPLETED;
    case WorkflowExecutionStatus.FAILED:
    case WorkflowExecutionStatus.TIMED_OUT:
    case WorkflowExecutionStatus.TERMINATED:
      return AgentStatus.FAILED;
    case WorkflowExecutionStatus.PAUSED:
      return AgentStatus.WAITING;
    default:
      return AgentStatus.RUNNING;
  }
}

/** Extract trailing iteration number from task name (e.g. "foo__3" → 3) */
function getIterationNum(name: string): number | null {
  const m = name.match(/__(\d+)$/);
  return m ? parseInt(m[1], 10) : null;
}

/** Extract agent name from sub-workflow task reference name.
 * HANDOFF pattern:  "workflow_handoff_0_planner_t1__1" → "planner_t1"
 * SWARM pattern:    "workflow_agent_1_engineering_lead__2" → "engineering_lead"
 * PARALLEL pattern: "coordinator_parallel_0_researcher" → "researcher"
 * Simple pattern:   "researcher__1" → "researcher"
 */
function extractAgentName(name: string): string | null {
  let m = name.match(/_handoff_\d+_(.+?)__\d+$/);
  if (m) return m[1];
  m = name.match(/_agent_\d+_(.+?)__\d+$/);
  if (m) return m[1];
  // Parallel fork: "coordinator_parallel_0_researcher" → "researcher"
  m = name.match(/_parallel_\d+_(.+?)(?:__\d+)?$/);
  if (m) return m[1];
  // Simple: strip __N iteration suffix → "researcher__1" → "researcher"
  m = name.match(/^(.+?)__\d+$/);
  return m ? m[1] : null;
}

/** All SUB_WORKFLOW tasks in agent context are sub-agents */
function isAgentSubWorkflow(task: ExecutionTask): boolean {
  return task.taskType === "SUB_WORKFLOW";
}

// ─── Sequential chain detection ──────────────────────────────────────────────

/** Returns the step index (N) if ref matches {chain}_step_{N}_{agent} SUB_WORKFLOW pattern */
function getChainStepNum(ref: string): number | null {
  const m = ref.match(/_step_(\d+)_/);
  return m ? parseInt(m[1], 10) : null;
}

/** Extract agent name from a chain step ref: {chain_name}_step_{N}_{agent_name} */
function getChainAgentName(ref: string): string | null {
  const m = ref.match(/_step_\d+_(.+)$/);
  return m ? m[1] : null;
}

/**
 * A chain workflow has SUB_WORKFLOW tasks with _step_N_ in their ref names,
 * no DO_WHILE, and no __N iteration suffix on those step tasks.
 */
function isChainWorkflow(tasks: ExecutionTask[]): boolean {
  const hasDoWhile = tasks.some((t) => t.taskType === "DO_WHILE");
  if (hasDoWhile) return false;
  return tasks.some(
    (t) =>
      t.taskType === "SUB_WORKFLOW" && /_step_\d+_/.test(t.referenceTaskName),
  );
}

/**
 * Transform a sequential chain workflow into AgentRunData.
 * Each step becomes a "turn" whose only sub-agent is the step agent.
 * Gate INLINE tasks become gate events within the turn.
 */
function transformChainWorkflowToAgentRun(
  execution: WorkflowExecution,
): AgentRunData {
  const tasks: ExecutionTask[] = execution.tasks ?? [];
  const startMs = toMs(execution.startTime);
  const endMs = toMs(execution.endTime);
  const isRunning = execution.status === WorkflowExecutionStatus.RUNNING;
  const totalDurationMs =
    endMs > 0
      ? endMs - startMs
      : isRunning
        ? Date.now() - startMs
        : (execution.executionTime ?? 0);

  // Collect step SUB_WORKFLOW tasks, indexed by step N
  const stepMap = new Map<number, ExecutionTask>();
  // Collect gate INLINE tasks, indexed by step N (gate_N evaluates output of step N)
  const gateMap = new Map<number, ExecutionTask>();

  for (const task of tasks) {
    const stepN = getChainStepNum(task.referenceTaskName);
    if (stepN !== null && task.taskType === "SUB_WORKFLOW") {
      stepMap.set(stepN, task);
    }
    // Gate INLINE: ref ends with _gate_{N} (not _gate_switch_{N})
    const gateM = task.referenceTaskName.match(/_gate_(\d+)$/);
    if (gateM && task.taskType === "INLINE") {
      gateMap.set(parseInt(gateM[1], 10), task);
    }
  }

  const sortedSteps = Array.from(stepMap.entries()).sort(([a], [b]) => a - b);

  // Grab the per-step agent configs from the definition metadata for gate label info
  const agentsDef = ((execution.workflowDefinition?.metadata?.agentDef as any)
    ?.agents ?? []) as Array<Record<string, unknown>>;

  const turns: AgentTurn[] = sortedSteps.map(
    ([stepN, task], idx): AgentTurn => {
      const agentName =
        getChainAgentName(task.referenceTaskName) ?? task.referenceTaskName;
      const subWorkflowId = task.outputData?.subWorkflowId as
        | string
        | undefined;
      const result = task.outputData?.result;
      const resultStr =
        typeof result === "string" && result.length > 0 ? result : undefined;
      const durationMs =
        task.endTime && task.startTime ? task.endTime - task.startTime : 0;

      const subAgent: AgentRunData = {
        id: subWorkflowId ?? task.taskId ?? `chain-step-${stepN}`,
        agentName,
        subWorkflowId,
        turns: resultStr
          ? [
              {
                turnNumber: 1,
                status: mapTaskStatus(task.status),
                durationMs,
                tokens: ZERO_TOKENS,
                subAgents: [],
                events: [
                  {
                    id: `${task.taskId}-msg`,
                    type: EventType.MESSAGE,
                    timestamp: task.startTime ?? 0,
                    summary:
                      resultStr.slice(0, 120) +
                      (resultStr.length > 120 ? "..." : ""),
                    detail: resultStr,
                    durationMs,
                  },
                ],
              },
            ]
          : [],
        status: mapTaskStatus(task.status),
        totalTokens: ZERO_TOKENS,
        totalDurationMs: durationMs,
        strategy: AgentStrategy.SINGLE,
        output: resultStr,
        failureReason: task.reasonForIncompletion ?? undefined,
      };

      const events: AgentEvent[] = [];

      // Gate event: evaluates the output of this step before proceeding to next
      const gateTask = gateMap.get(stepN);
      if (gateTask) {
        const gateResult = gateTask.outputData?.result as
          | { decision?: string }
          | undefined;
        const decision = gateResult?.decision ?? "continue";
        const isStop = decision === "stop";
        const gateDuration =
          gateTask.endTime && gateTask.startTime
            ? gateTask.endTime - gateTask.startTime
            : 0;
        const stepAgentDef = agentsDef[stepN];
        const gateCfg = stepAgentDef?.gate as
          | Record<string, unknown>
          | undefined;
        const sentinel = gateCfg?.text ? `"${gateCfg.text}"` : "";
        const gateLabel = isStop
          ? `Gate${sentinel ? ` ${sentinel}` : ""} matched — chain stopped here`
          : `Gate${sentinel ? ` ${sentinel}` : ""} not matched — chain continues`;

        events.push({
          id: `${gateTask.taskId}-gate`,
          type: isStop ? EventType.GUARDRAIL_FAIL : EventType.GUARDRAIL_PASS,
          timestamp: gateTask.startTime ?? 0,
          summary: gateLabel,
          toolName: "gate",
          success: !isStop,
          durationMs: gateDuration,
          detail: { decision, ...(gateCfg ? { gate: gateCfg } : {}) },
        });
      }

      const timestamps = [
        task.startTime,
        task.endTime,
        gateTask?.startTime,
        gateTask?.endTime,
      ].filter((v): v is number => v != null && v > 0);

      return {
        turnNumber: idx + 1,
        events,
        status: mapTaskStatus(task.status),
        durationMs: timestamps.length
          ? Math.max(...timestamps) - Math.min(...timestamps)
          : durationMs,
        tokens: ZERO_TOKENS,
        subAgents: [subAgent],
        strategy: AgentStrategy.SEQUENTIAL,
      };
    },
  );

  const agentDef = execution.workflowDefinition?.metadata?.agentDef as
    | Record<string, unknown>
    | undefined;
  const finishReason =
    execution.status === WorkflowExecutionStatus.COMPLETED
      ? FinishReason.STOP
      : execution.status === WorkflowExecutionStatus.FAILED ||
          execution.status === WorkflowExecutionStatus.TIMED_OUT ||
          execution.status === WorkflowExecutionStatus.TERMINATED
        ? FinishReason.ERROR
        : undefined;
  const execInput = execution.input as any;
  const agentInput: string | undefined =
    typeof execInput === "string"
      ? execInput || undefined
      : typeof execInput === "object" && execInput !== null
        ? execInput.prompt ||
          execInput.conversation ||
          execInput.message ||
          undefined
        : undefined;

  // Root output: last step's result, or workflow output field
  const lastStep = sortedSteps[sortedSteps.length - 1];
  let chainOutput: string | undefined;
  if (lastStep) {
    const r = lastStep[1].outputData?.result;
    if (typeof r === "string" && r.length > 0) chainOutput = r;
  }
  if (!chainOutput && execution.output) {
    const wfOut = execution.output as any;
    const candidate = wfOut.result ?? wfOut.output ?? wfOut.message;
    if (typeof candidate === "string" && (candidate as string).length > 0)
      chainOutput = candidate as string;
  }

  const chainModel =
    (agentDef?.model as string | undefined) ??
    (tasks.find((t) => t.taskType === "LLM_CHAT_COMPLETE")?.inputData?.model as
      | string
      | undefined);

  return {
    id: execution.workflowId,
    agentName: execution.workflowName ?? execution.workflowType ?? "agent",
    model: chainModel,
    turns,
    status: mapWorkflowStatus(execution.status),
    agentDef,
    totalTokens: ZERO_TOKENS,
    totalDurationMs,
    finishReason,
    strategy: AgentStrategy.SEQUENTIAL,
    input: agentInput,
    output: chainOutput,
  };
}

/**
 * Transform a top-level WorkflowExecution into AgentRunData for the Agent Execution tab.
 * Groups tasks by DO_WHILE iteration; each iteration becomes one turn.
 * Each handoff SUB_WORKFLOW task within an iteration becomes a sub-agent.
 */
export function transformWorkflowExecutionToAgentRun(
  execution: WorkflowExecution,
): AgentRunData {
  const tasks: ExecutionTask[] = execution.tasks ?? [];

  // Sequential chain: delegate to dedicated transformer
  if (isChainWorkflow(tasks)) {
    return transformChainWorkflowToAgentRun(execution);
  }

  const startMs = toMs(execution.startTime);
  const endMs = toMs(execution.endTime);
  const isRunning = execution.status === WorkflowExecutionStatus.RUNNING;
  const totalDurationMs =
    endMs > 0
      ? endMs - startMs
      : isRunning
        ? Date.now() - startMs
        : (execution.executionTime ?? 0);

  // Infrastructure task types to skip everywhere
  const ITER_INFRA = new Set([
    "SET_VARIABLE",
    "SWITCH",
    "INLINE",
    "DO_WHILE",
    "FORK",
    "FORK_JOIN",
    "FORK_JOIN_DYNAMIC",
    "JOIN",
  ]);

  // Build set of guardrail function names from agentDef for robust detection
  const agentDefMeta = execution.workflowDefinition?.metadata?.agentDef as
    | Record<string, unknown>
    | undefined;
  const guardrailFnNames = new Set<string>();
  for (const gList of [
    (agentDefMeta?.input_guardrails as
      | Array<Record<string, unknown>>
      | undefined) ?? [],
    (agentDefMeta?.output_guardrails as
      | Array<Record<string, unknown>>
      | undefined) ?? [],
    (agentDefMeta?.guardrails as Array<Record<string, unknown>> | undefined) ??
      [],
  ]) {
    for (const g of gList) {
      const fn = (g.guardrail_function ?? g) as Record<string, unknown>;
      const name = (fn._worker_ref ?? fn.name) as string | undefined;
      if (name) guardrailFnNames.add(name.toLowerCase());
    }
  }

  // Group tasks by DO_WHILE iteration number; collect root-level tasks separately
  const iterMap = new Map<number, ExecutionTask[]>();
  const rootActiveTasks: ExecutionTask[] = [];
  for (const task of tasks) {
    const iter = getIterationNum(task.referenceTaskName);
    if (iter !== null) {
      if (!iterMap.has(iter)) iterMap.set(iter, []);
      iterMap.get(iter)!.push(task);
    } else if (!ITER_INFRA.has(task.taskType)) {
      // Root-level non-infrastructure: final LLM tasks, or entire simple agents
      rootActiveTasks.push(task);
    }
  }

  const sortedIters = Array.from(iterMap.entries()).sort(([a], [b]) => a - b);

  // The root agent's own name — used to detect SWARM self-calls
  const rootAgentName: string =
    execution.workflowName ?? execution.workflowType ?? "";

  const turns: AgentTurn[] = sortedIters
    .map(([, iterTasks], idx) => {
      const agentTasks = iterTasks.filter(isAgentSubWorkflow);

      // LLM tasks directly in this iteration (tool-calling agent pattern)
      // Dedup retried tasks so timed-out attempts don't appear as parallel forks
      const { tasks: iterLlmTasks } = deduplicateRetriedTasks(
        iterTasks.filter((t) => t.taskType === "LLM_CHAT_COMPLETE"),
      );

      // Tool worker tasks — any non-infra, non-subworkflow, non-LLM task
      const {
        tasks: toolWorkerTasks,
        attemptCounts: toolAttemptCounts,
        attemptGroups: toolAttemptGroups,
      } = deduplicateRetriedTasks(
        iterTasks.filter(
          (t) =>
            !ITER_INFRA.has(t.taskType) &&
            t.taskType !== "SUB_WORKFLOW" &&
            t.taskType !== "LLM_CHAT_COMPLETE",
        ),
      );

      // SWARM self-calls: iterations where the agent IS the root agent itself
      // (e.g. CEO calling itself to make a routing decision)
      const selfCalls = agentTasks.filter(
        (t) => extractAgentName(t.referenceTaskName) === rootAgentName,
      );
      // Real sub-agent calls: exclude router tasks (orchestration machinery)
      // and self-calls — both are handled separately above/below.
      const subAgentTasks = agentTasks.filter(
        (t) =>
          extractAgentName(t.referenceTaskName) !== rootAgentName &&
          !t.referenceTaskName.includes("_router_"),
      );

      const events: AgentEvent[] = [];

      // Self-calls → HANDOFF events (the agent deciding where to route)
      for (const task of selfCalls) {
        const transferTo = task.outputData?.transfer_to as string | undefined;
        const isTransfer = task.outputData?.is_transfer as boolean | undefined;
        const durationMs =
          task.endTime && task.startTime ? task.endTime - task.startTime : 0;

        if (isTransfer && transferTo) {
          events.push({
            id: `${task.taskId}-handoff`,
            type: EventType.HANDOFF,
            timestamp: task.endTime ?? 0,
            summary: `→ ${transferTo}`,
            targetAgent: transferTo,
            detail: { transfer_to: transferTo, agent: rootAgentName },
            durationMs,
          });
        } else {
          // Self-call with no transfer → agent responded directly (final response)
          events.push({
            id: `${task.taskId}-msg`,
            type: EventType.MESSAGE,
            timestamp: task.endTime ?? 0,
            summary: "Agent responded",
            durationMs,
          });
        }
      }

      // Also handle HANDOFF-strategy router tasks (team_t1 style)
      if (selfCalls.length === 0) {
        const routerTask = iterTasks.find((t) =>
          t.referenceTaskName.includes("_router_"),
        );
        const routingDecision = routerTask?.outputData?.result as
          | string
          | undefined;
        if (routingDecision && subAgentTasks.length > 0) {
          events.push({
            id: `iter-${idx}-route`,
            type: EventType.HANDOFF,
            timestamp: routerTask?.endTime ?? 0,
            summary: `→ ${routingDecision}`,
            targetAgent: routingDecision,
            detail: { routing_decision: routingDecision },
          });
        }
      }

      // Build sub-agents from real sub-agent calls
      const subAgents: AgentRunData[] = subAgentTasks.map(
        (task): AgentRunData => {
          // Prefer subWorkflowName from inputData (Conductor populates this with the workflow name).
          // For "agent-as-tool" tasks (ref name = call_{toolCallId}__N), workflowTask.name holds
          // the actual agent/sub-workflow name (e.g. "deep_analyst_68").
          // Otherwise fall back to regex extraction then raw reference name.
          const isAgentAsTool = /^call_[A-Za-z0-9]+__\d+$/.test(
            task.referenceTaskName,
          );
          const agentName =
            (task.inputData?.subWorkflowName as string | undefined) ??
            (isAgentAsTool ? task.workflowTask?.name : null) ??
            extractAgentName(task.referenceTaskName) ??
            task.referenceTaskName;
          const subWorkflowId = task.outputData?.subWorkflowId as
            | string
            | undefined;
          const result = task.outputData?.result;
          const resultStr =
            typeof result === "string" && result.length > 0
              ? result
              : undefined;
          // Agent-as-tool: input is nested under workflowInput.prompt
          const agentInput = isAgentAsTool
            ? ((task.inputData as any)?.workflowInput?.prompt as
                | string
                | undefined)
            : undefined;
          const durationMs =
            task.endTime && task.startTime ? task.endTime - task.startTime : 0;

          const subTurns: AgentTurn[] = resultStr
            ? [
                {
                  turnNumber: 1,
                  status: mapTaskStatus(task.status),
                  durationMs,
                  tokens: ZERO_TOKENS,
                  subAgents: [],
                  events: [
                    {
                      id: `${task.taskId}-msg`,
                      type: EventType.MESSAGE,
                      timestamp: task.startTime ?? 0,
                      summary:
                        resultStr.slice(0, 120) +
                        (resultStr.length > 120 ? "..." : ""),
                      detail: resultStr,
                      durationMs,
                    },
                    {
                      id: `${task.taskId}-done`,
                      type: EventType.DONE,
                      timestamp: task.endTime ?? 0,
                      summary: "Agent completed",
                      success: task.status === "COMPLETED",
                    },
                  ],
                },
              ]
            : [];

          return {
            id: subWorkflowId ?? task.taskId ?? `sub-${agentName}`,
            agentName,
            subWorkflowId,
            turns: subTurns,
            status: mapTaskStatus(task.status),
            totalTokens: ZERO_TOKENS,
            totalDurationMs: durationMs,
            strategy: AgentStrategy.SINGLE,
            input: agentInput,
            output: resultStr,
            failureReason: task.reasonForIncompletion ?? undefined,
          };
        },
      );

      // LLM events: one block per LLM call showing input + output
      for (const llmTask of iterLlmTasks) {
        const condensed = maybeCondensationEvent(llmTask);
        if (condensed) events.push(condensed);

        const model = llmTask.inputData?.model as string | undefined;
        const llmBaseUrl = llmTask.inputData?.baseUrl as string | undefined;
        const finishReason = (
          (llmTask.outputData?.finishReason as string) ?? "stop"
        ).toLowerCase();
        const result = llmTask.outputData?.result;
        const promptTokens = (llmTask.outputData?.promptTokens as number) || 0;
        const completionTokens =
          (llmTask.outputData?.completionTokens as number) || 0;
        const messages =
          (llmTask.inputData?.messages as Array<{
            role: string;
            message: string;
          }>) ?? [];
        const tools = (llmTask.inputData?.tools as unknown[]) ?? [];
        const llmDuration =
          llmTask.endTime && llmTask.startTime
            ? llmTask.endTime - llmTask.startTime
            : 0;
        const isStop = finishReason === "stop";

        // Show instructions (system prompt) + last user message only
        const sysMsg = messages.find((m) => m.role === "system");
        const lastMsg = [...messages]
          .reverse()
          .find((m) => m.role !== "system");

        // ONE block: LLM call — instructions + last message + output
        events.push({
          id: `${llmTask.taskId}-llm`,
          type: EventType.THINKING,
          timestamp: llmTask.startTime ?? 0,
          toolName: model,
          baseUrl: llmBaseUrl,
          summary: `${model ?? "LLM"} · ${messages.length} messages${tools.length ? ` · ${tools.length} tools` : ""}`,
          detail: {
            input: {
              ...(sysMsg ? { instructions: sysMsg.message } : {}),
              ...(lastMsg ? { message: lastMsg.message } : {}),
            },
            output: llmTask.outputData,
          },
          result: isStop && typeof result === "string" ? result : result,
          tokens: {
            promptTokens,
            completionTokens,
            totalTokens: promptTokens + completionTokens,
          },
          durationMs: llmDuration,
          success: taskSuccess(llmTask.status),
          condensationInfo: condensed?.condensationInfo,
        });

        // If the LLM returned a text response, show it as "Output" (DONE event)
        if (isStop && typeof result === "string" && result.length > 0) {
          events.push({
            id: `${llmTask.taskId}-output`,
            type: EventType.DONE,
            timestamp: llmTask.endTime ?? 0,
            summary: result.slice(0, 120) + (result.length > 120 ? "..." : ""),
            detail: result,
            success: true,
            tokens: {
              promptTokens,
              completionTokens,
              totalTokens: promptTokens + completionTokens,
            },
            durationMs: llmDuration,
          });
        }
      }

      // Tool worker events — ONE combined block per call showing input + output
      // Track whether a HANDOFF was already emitted this turn to avoid duplicates.
      let handoffEmittedThisTurn = false;

      for (const toolTask of toolWorkerTasks) {
        const idData = (toolTask.inputData ?? {}) as Record<string, unknown>;
        const od = (toolTask.outputData ?? {}) as Record<string, unknown>;
        const toolName = toolTask.taskType;
        const failed = isFailedTaskStatus(toolTask.status);
        const toolDuration =
          toolTask.endTime && toolTask.startTime
            ? toolTask.endTime - toolTask.startTime
            : 0;

        // Strip only the large internal state blob; keep method + actual args
        const cleanInput = Object.fromEntries(
          Object.entries(idData).filter(([k]) => k !== "_agent_state"),
        );

        // ── Handoff / transfer tools ──────────────────────────────────────────
        // Tools named transfer_to_* or handoff_to_* are agent-handoff mechanisms,
        // not real user tools. Convert them to a HANDOFF event.
        const isHandoffTool =
          /^(transfer_to_|handoff_to_|route_to_|delegate_to_)/i.test(toolName);
        if (isHandoffTool) {
          // Extract target agent name: everything after the prefix
          const target =
            toolName
              .replace(
                /^(transfer_to_|handoff_to_|route_to_|delegate_to_)/i,
                "",
              )
              .replace(/_/g, " ")
              .trim() || toolName;
          if (!handoffEmittedThisTurn) {
            events.push({
              id: `${toolTask.taskId}-handoff`,
              type: EventType.HANDOFF,
              timestamp: toolTask.endTime ?? 0,
              summary: `→ ${target}`,
              targetAgent: target,
              detail: { transfer_to: target },
              durationMs: toolDuration,
            });
            handoffEmittedThisTurn = true;
          }
          continue;
        }

        // ── Transfer-check tasks (e.g. coder_check_transfer__N) ───────────────
        // These are orchestration infrastructure tasks that confirm a handoff decision.
        // Detected by output shape { is_transfer: bool, transfer_to: string }.
        // Emit HANDOFF only as a fallback when no handoff tool ran.
        const isTransferCheck = "is_transfer" in od;
        if (isTransferCheck) {
          const isTransfer = od.is_transfer === true;
          const transferTo = od.transfer_to as string | undefined;
          if (isTransfer && transferTo && !handoffEmittedThisTurn) {
            events.push({
              id: `${toolTask.taskId}-handoff`,
              type: EventType.HANDOFF,
              timestamp: toolTask.endTime ?? 0,
              summary: `→ ${transferTo}`,
              targetAgent: transferTo,
              detail: { transfer_to: transferTo },
              durationMs: toolDuration,
            });
            handoffEmittedThisTurn = true;
          }
          // Always skip — pure orchestration infra, never show as a tool call
          continue;
        }

        // ── Detect guardrail tasks by name convention or agentDef declaration ──
        const refName = (toolTask.referenceTaskName ?? "").toLowerCase();
        const isGuardrail =
          toolName.toLowerCase().includes("guardrail") ||
          refName.includes("guardrail") ||
          guardrailFnNames.has(toolName.toLowerCase());
        if (isGuardrail) {
          // Extract the actual content the guardrail checked (strip redundant alias fields)
          const guardrailInput =
            idData.content ??
            idData.agent_output ??
            idData.input_text ??
            idData.output ??
            idData.input;
          // Look for the INLINE evaluation result in the same iteration
          const evalRefPrefix = refName.replace(/_worker(_|$)/, "$1");
          const evalTask = iterTasks.find(
            (t) =>
              t.taskType === "INLINE" &&
              (t.referenceTaskName ?? "")
                .toLowerCase()
                .startsWith(evalRefPrefix),
          );
          const evalResult = evalTask?.outputData?.result as
            | Record<string, unknown>
            | undefined;
          // Build a clean output: merge worker output with evaluation result
          const guardrailOutput = evalResult ?? od;

          // Guardrail "failure" is expressed in the output data, not the task status.
          // The worker task COMPLETES even when the guardrail triggers — check output fields.
          const guardrailTriggered =
            failed ||
            od.tripwire_triggered === true ||
            evalResult?.passed === false;
          const reason =
            (evalResult?.message as string | undefined) ??
            (od.output_info as any)?.reason ??
            (guardrailTriggered
              ? (toolTask.reasonForIncompletion ?? "content blocked")
              : "passed");

          events.push({
            id: `${toolTask.taskId}-guardrail`,
            type: guardrailTriggered
              ? EventType.GUARDRAIL_FAIL
              : EventType.GUARDRAIL_PASS,
            timestamp: toolTask.startTime ?? 0,
            toolName,
            summary: guardrailTriggered
              ? `${toolName} blocked: ${reason}`
              : `${toolName} — ${reason}`,
            detail: { input: guardrailInput, output: guardrailOutput },
            success: !guardrailTriggered,
            durationMs: toolDuration,
            taskMeta: {
              taskId: toolTask.taskId,
              taskType: toolTask.taskType,
              referenceTaskName: toolTask.referenceTaskName,
              scheduledTime: toolTask.scheduledTime ?? undefined,
              startTime: toolTask.startTime ?? undefined,
              endTime: toolTask.endTime ?? undefined,
              workerId: toolTask.workerId ?? undefined,
              reasonForIncompletion:
                toolTask.reasonForIncompletion ?? undefined,
              retryCount: toolTask.retryCount,
              totalAttempts: toolAttemptCounts.get(toolTask.referenceTaskName),
              allAttempts: buildAllAttempts(
                toolTask.referenceTaskName,
                toolAttemptGroups,
              ),
              pollCount: toolTask.pollCount,
              seq: toolTask.seq,
              queueWaitTime: toolTask.queueWaitTime,
            },
          });
          continue;
        }

        // Build a readable output preview for the summary line
        const outputPreview = failed
          ? `Error: ${toolTask.reasonForIncompletion ?? "failed"}`
          : (() => {
              // Prefer unwrapped result if the only key is 'result'
              const keys = Object.keys(od);
              const val =
                keys.length === 1 && keys[0] === "result" ? od["result"] : od;
              try {
                return (JSON.stringify(val) ?? "").slice(0, 80);
              } catch {
                return "[complex data]";
              }
            })();

        const toolTotalAttempts = toolAttemptCounts.get(
          toolTask.referenceTaskName,
        );
        events.push({
          id: `${toolTask.taskId}-tool`,
          type: EventType.TOOL_CALL,
          timestamp: toolTask.startTime ?? 0,
          toolName,
          summary: `${toolName} → ${outputPreview}`,
          detail: {
            input: cleanInput,
            output: failed ? toolTask.reasonForIncompletion : od,
          },
          toolArgs: cleanInput,
          result: failed ? undefined : od,
          success: taskSuccess(toolTask.status),
          durationMs: toolDuration,
          taskMeta: {
            taskId: toolTask.taskId,
            taskType: toolTask.taskType,
            referenceTaskName: toolTask.referenceTaskName,
            scheduledTime: toolTask.scheduledTime ?? undefined,
            startTime: toolTask.startTime ?? undefined,
            endTime: toolTask.endTime ?? undefined,
            workerId: toolTask.workerId ?? undefined,
            reasonForIncompletion: toolTask.reasonForIncompletion ?? undefined,
            retryCount: toolTask.retryCount,
            totalAttempts: toolTotalAttempts,
            allAttempts: buildAllAttempts(
              toolTask.referenceTaskName,
              toolAttemptGroups,
            ),
            pollCount: toolTask.pollCount,
            seq: toolTask.seq,
            queueWaitTime: toolTask.queueWaitTime,
          },
        });
      }

      // If this iteration has no meaningful events (e.g. a done_noop termination
      // iteration), produce nothing — the turn will be filtered out below.

      const timestamps = iterTasks
        .flatMap((t) => [t.startTime, t.endTime])
        .filter((v): v is number => v != null && v > 0);
      const turnStart = timestamps.length ? Math.min(...timestamps) : 0;
      const turnEnd = timestamps.length ? Math.max(...timestamps) : 0;

      // Token counts from LLM tasks in this iteration
      const turnPromptTokens = iterLlmTasks.reduce(
        (s, t) => s + ((t.outputData?.promptTokens as number) || 0),
        0,
      );
      const turnCompletionTokens = iterLlmTasks.reduce(
        (s, t) => s + ((t.outputData?.completionTokens as number) || 0),
        0,
      );

      const subStatuses = subAgents.map((s) => s.status);
      const anyRunning =
        subStatuses.includes(AgentStatus.RUNNING) ||
        iterTasks.some((t) => t.status === "IN_PROGRESS");
      const anyFailed =
        subStatuses.includes(AgentStatus.FAILED) ||
        iterTasks.some((t) => isFailedTaskStatus(t.status));
      const turnStatus = anyRunning
        ? AgentStatus.RUNNING
        : anyFailed
          ? AgentStatus.FAILED
          : AgentStatus.COMPLETED;

      return {
        turnNumber: idx + 1,
        events,
        status: turnStatus,
        durationMs: turnEnd > turnStart ? turnEnd - turnStart : 0,
        tokens: {
          promptTokens: turnPromptTokens,
          completionTokens: turnCompletionTokens,
          totalTokens: turnPromptTokens + turnCompletionTokens,
        },
        subAgents,
        strategy:
          subAgents.length > 1 ? AgentStrategy.PARALLEL : AgentStrategy.HANDOFF,
      };
    })
    .filter((t) => t.events.length > 0 || t.subAgents.length > 0);

  // Build sub-agents from root-level SUB_WORKFLOW tasks (merged into root events turn below).
  const rootSubWorkflows = rootActiveTasks.filter(isAgentSubWorkflow);
  const rootSubAgents: AgentRunData[] = rootSubWorkflows.map((task) => {
    const agentName =
      extractAgentName(task.referenceTaskName) ??
      (task.inputData?.subWorkflowName as string) ??
      task.workflowTask?.name ??
      task.referenceTaskName;
    const subWfId = task.outputData?.subWorkflowId as string | undefined;
    const dur =
      task.endTime && task.startTime ? task.endTime - task.startTime : 0;

    // Extract input from workflowInput (Claude Code / agent-as-tool pattern)
    const wfInput = task.inputData?.workflowInput as
      | Record<string, unknown>
      | undefined;
    const agentInput =
      (wfInput?.prompt as string | undefined) ??
      (wfInput?.description as string | undefined) ??
      undefined;

    // Extract output: try result, then tool_response content blocks
    let outputStr: string | undefined;
    const directResult = task.outputData?.result;
    if (typeof directResult === "string" && directResult.length > 0) {
      outputStr = directResult;
    } else {
      const toolResp = task.outputData?.tool_response as
        | Record<string, unknown>
        | undefined;
      if (toolResp) {
        const content = toolResp.content as
          | Array<Record<string, unknown>>
          | undefined;
        if (Array.isArray(content)) {
          const textBlock = content.find((c) => c.type === "text");
          if (textBlock && typeof (textBlock as any).text === "string") {
            outputStr = (textBlock as any).text;
          }
        }
        if (!outputStr && typeof toolResp.result === "string") {
          outputStr = toolResp.result as string;
        }
      }
    }

    const failReason = task.reasonForIncompletion ?? undefined;

    const subTurns: AgentTurn[] = outputStr
      ? [
          {
            turnNumber: 1,
            status: mapTaskStatus(task.status),
            durationMs: dur,
            tokens: ZERO_TOKENS,
            subAgents: [],
            events: [
              {
                id: `${task.taskId}-msg`,
                type: EventType.MESSAGE,
                timestamp: task.startTime ?? 0,
                summary:
                  outputStr.slice(0, 120) +
                  (outputStr.length > 120 ? "..." : ""),
                detail: outputStr,
                durationMs: dur,
              },
              {
                id: `${task.taskId}-done`,
                type: EventType.DONE,
                timestamp: task.endTime ?? 0,
                summary: "Agent completed",
                success: task.status === "COMPLETED",
              },
            ],
          },
        ]
      : [];

    return {
      id: subWfId ?? task.taskId,
      subWorkflowId: subWfId,
      agentName,
      turns: subTurns,
      status: mapTaskStatus(task.status),
      totalTokens: ZERO_TOKENS,
      totalDurationMs: dur,
      input: agentInput,
      output: outputStr,
      failureReason: failReason,
    } as AgentRunData;
  });

  // Build events + turn for root-level tasks (outside DO_WHILE).
  // This handles: simple single-LLM agents (greeter, triage_router_wf),
  // final synthesis tasks, and Claude Code agent tool calls + sub-agents.
  // Sub-agents are included in the same turn to preserve execution order.
  // Dedup retried tasks so timed-out attempts don't duplicate events.
  const {
    tasks: dedupedRootTasks,
    attemptCounts: rootAttemptCounts,
    attemptGroups: rootAttemptGroups,
  } = deduplicateRetriedTasks(rootActiveTasks);
  let finalOutput: string | undefined;
  if (dedupedRootTasks.length > 0) {
    const rootEvents: AgentEvent[] = [];
    let rootPrompt = 0,
      rootCompletion = 0;

    for (const task of dedupedRootTasks) {
      // Skip the framework task (_fw_task) — it represents the agent itself, not a tool call.
      // Its outputData is used for the final agent output below.
      if (task.referenceTaskName === "_fw_task") continue;

      if (task.taskType === "LLM_CHAT_COMPLETE") {
        const condensed = maybeCondensationEvent(task);
        if (condensed) rootEvents.push(condensed);

        const model = task.inputData?.model as string | undefined;
        const rootBaseUrl = task.inputData?.baseUrl as string | undefined;
        const finishReason = (
          (task.outputData?.finishReason as string) ?? "stop"
        ).toLowerCase();
        const result = task.outputData?.result;
        const promptTokens = (task.outputData?.promptTokens as number) || 0;
        const completionTokens =
          (task.outputData?.completionTokens as number) || 0;
        const messages =
          (task.inputData?.messages as Array<{
            role: string;
            message: string;
          }>) ?? [];
        const tools = (task.inputData?.tools as unknown[]) ?? [];
        const dur =
          task.endTime && task.startTime ? task.endTime - task.startTime : 0;
        rootPrompt += promptTokens;
        rootCompletion += completionTokens;

        const rootSysMsg = messages.find((m) => m.role === "system");
        const rootLastMsg = [...messages]
          .reverse()
          .find((m) => m.role !== "system");

        rootEvents.push({
          id: `${task.taskId}-llm`,
          type: EventType.THINKING,
          toolName: model,
          baseUrl: rootBaseUrl,
          timestamp: task.startTime ?? 0,
          summary: `${model ?? "LLM"} · ${messages.length} messages${tools.length ? ` · ${tools.length} tools` : ""}`,
          detail: {
            input: {
              ...(rootSysMsg ? { instructions: rootSysMsg.message } : {}),
              ...(rootLastMsg ? { message: rootLastMsg.message } : {}),
            },
            output: task.outputData,
          },
          tokens: {
            promptTokens,
            completionTokens,
            totalTokens: promptTokens + completionTokens,
          },
          durationMs: dur,
          success: taskSuccess(task.status),
          condensationInfo: condensed?.condensationInfo,
        });

        if (
          finishReason === "stop" &&
          typeof result === "string" &&
          result.length > 0
        ) {
          finalOutput = result;
          rootEvents.push({
            id: `${task.taskId}-output`,
            type: EventType.DONE,
            timestamp: task.endTime ?? 0,
            summary: result.slice(0, 120) + (result.length > 120 ? "..." : ""),
            detail: result,
            success: true,
            tokens: {
              promptTokens,
              completionTokens,
              totalTokens: promptTokens + completionTokens,
            },
            durationMs: dur,
          });
        }
      } else if (
        !ITER_INFRA.has(task.taskType) &&
        task.taskType !== "SUB_WORKFLOW"
      ) {
        // Root-level tool worker task (no DO_WHILE iteration suffix)
        const od = (task.outputData ?? {}) as Record<string, unknown>;
        const idData = (task.inputData ?? {}) as Record<string, unknown>;
        const failed = isFailedTaskStatus(task.status);
        const dur =
          task.endTime && task.startTime ? task.endTime - task.startTime : 0;
        const cleanInput = Object.fromEntries(
          Object.entries(idData).filter(([k]) => k !== "_agent_state"),
        );

        // Handoff/transfer tools → HANDOFF event, not TOOL_CALL
        if (
          /^(transfer_to_|handoff_to_|route_to_|delegate_to_)/i.test(
            task.taskType,
          )
        ) {
          const target = task.taskType
            .replace(/^(transfer_to_|handoff_to_|route_to_|delegate_to_)/i, "")
            .replace(/_/g, " ")
            .trim();
          rootEvents.push({
            id: `${task.taskId}-handoff`,
            type: EventType.HANDOFF,
            timestamp: task.endTime ?? 0,
            summary: `→ ${target}`,
            targetAgent: target,
            detail: { transfer_to: target },
            durationMs: dur,
          });
          // Transfer-check infra tasks → emit HANDOFF only if is_transfer=true, else skip
        } else if ("is_transfer" in od) {
          if (od.is_transfer === true && od.transfer_to) {
            rootEvents.push({
              id: `${task.taskId}-handoff`,
              type: EventType.HANDOFF,
              timestamp: task.endTime ?? 0,
              summary: `→ ${od.transfer_to}`,
              targetAgent: od.transfer_to as string,
              detail: { transfer_to: od.transfer_to },
              durationMs: dur,
            });
          }
          // skip regardless
        } else {
          const isGuardrail = task.taskType.toLowerCase().includes("guardrail");
          if (isGuardrail) {
            rootEvents.push({
              id: `${task.taskId}-guardrail`,
              type: failed
                ? EventType.GUARDRAIL_FAIL
                : EventType.GUARDRAIL_PASS,
              timestamp: task.startTime ?? 0,
              toolName: task.taskType,
              summary: failed
                ? `${task.taskType} blocked: ${task.reasonForIncompletion ?? "content blocked"}`
                : `${task.taskType} passed`,
              detail: {
                input: cleanInput,
                output: failed ? task.reasonForIncompletion : od,
              },
              success: !failed,
              durationMs: dur,
            });
          } else {
            rootEvents.push({
              id: `${task.taskId}-tool`,
              type: EventType.TOOL_CALL,
              timestamp: task.startTime ?? 0,
              toolName: task.taskType,
              summary: task.taskType,
              detail: {
                input: cleanInput,
                output: failed ? task.reasonForIncompletion : od,
              },
              toolArgs: cleanInput,
              result: failed ? undefined : od,
              success: taskSuccess(task.status),
              durationMs: dur,
              taskMeta: {
                taskId: task.taskId,
                taskType: task.taskType,
                referenceTaskName: task.referenceTaskName,
                scheduledTime: task.scheduledTime ?? undefined,
                startTime: task.startTime ?? undefined,
                endTime: task.endTime ?? undefined,
                workerId: task.workerId ?? undefined,
                reasonForIncompletion: task.reasonForIncompletion ?? undefined,
                retryCount: task.retryCount,
                totalAttempts: rootAttemptCounts.get(task.referenceTaskName),
                allAttempts: buildAllAttempts(
                  task.referenceTaskName,
                  rootAttemptGroups,
                ),
                pollCount: task.pollCount,
                seq: task.seq,
                queueWaitTime: task.queueWaitTime,
              },
            });
          }
        }
      }
    }

    if (rootEvents.length > 0 || rootSubAgents.length > 0) {
      const rootTimestamps = rootActiveTasks
        .flatMap((t) => [t.startTime, t.endTime])
        .filter((v): v is number => v != null && v > 0);
      turns.push({
        turnNumber: turns.length + 1,
        events: rootEvents,
        status: AgentStatus.COMPLETED,
        durationMs: rootTimestamps.length
          ? Math.max(...rootTimestamps) - Math.min(...rootTimestamps)
          : 0,
        tokens: {
          promptTokens: rootPrompt,
          completionTokens: rootCompletion,
          totalTokens: rootPrompt + rootCompletion,
        },
        subAgents: rootSubAgents,
      });
    }
  }

  // Check the framework task (_fw_task) for output — Claude Code agent pattern
  if (!finalOutput) {
    const fwTask = rootActiveTasks.find(
      (t) => t.referenceTaskName === "_fw_task",
    );
    const fwResult = fwTask?.outputData?.result;
    if (typeof fwResult === "string" && fwResult.length > 0) {
      finalOutput = fwResult;
    }
  }

  // If no finalOutput found from root tasks, check last iteration's final STOP LLM
  if (!finalOutput && turns.length > 0) {
    for (const event of [...turns[turns.length - 1].events].reverse()) {
      if (event.type === EventType.DONE && typeof event.detail === "string") {
        finalOutput = event.detail;
        break;
      }
    }
  }

  // Last resort: check the Conductor workflow output field
  if (!finalOutput && execution.output) {
    const wfOut = execution.output as any;
    const candidate = wfOut.result ?? wfOut.output ?? wfOut.message;
    if (typeof candidate === "string" && candidate.length > 0) {
      finalOutput = candidate;
    }
  }

  // Extract the initial user prompt from execution input
  const execInput = execution.input as any;
  const agentInput: string | undefined =
    typeof execInput === "string"
      ? execInput || undefined
      : typeof execInput === "object" && execInput !== null
        ? execInput.prompt ||
          execInput.conversation ||
          execInput.message ||
          undefined
        : undefined;

  // Accumulate total tokens from all turns
  const totalPromptTokens = turns.reduce(
    (s, t) => s + t.tokens.promptTokens,
    0,
  );
  const totalCompletionTokens = turns.reduce(
    (s, t) => s + t.tokens.completionTokens,
    0,
  );

  const finishReason =
    execution.status === WorkflowExecutionStatus.COMPLETED
      ? FinishReason.STOP
      : execution.status === WorkflowExecutionStatus.FAILED ||
          execution.status === WorkflowExecutionStatus.TIMED_OUT ||
          execution.status === WorkflowExecutionStatus.TERMINATED
        ? FinishReason.ERROR
        : undefined;

  const agentDef = execution.workflowDefinition?.metadata?.agentDef as
    | Record<string, unknown>
    | undefined;

  // Extract model from agentDef metadata or from first LLM task
  const agentModel =
    (agentDef?.model as string | undefined) ??
    (tasks.find((t) => t.taskType === "LLM_CHAT_COMPLETE")?.inputData?.model as
      | string
      | undefined);

  return {
    id: execution.workflowId,
    agentName: execution.workflowName ?? execution.workflowType ?? "agent",
    model: agentModel,
    turns,
    status: mapWorkflowStatus(execution.status),
    agentDef,
    totalTokens: {
      promptTokens: totalPromptTokens,
      completionTokens: totalCompletionTokens,
      totalTokens: totalPromptTokens + totalCompletionTokens,
    },
    totalDurationMs,
    finishReason,
    strategy:
      rootSubWorkflows.length > 1
        ? AgentStrategy.PARALLEL
        : sortedIters.length > 0
          ? AgentStrategy.HANDOFF
          : AgentStrategy.SINGLE,
    input: agentInput,
    output: finalOutput,
  };
}
