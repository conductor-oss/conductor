export enum EventType {
  THINKING = "THINKING",
  TOOL_CALL = "TOOL_CALL",
  TOOL_RESULT = "TOOL_RESULT",
  GUARDRAIL_PASS = "GUARDRAIL_PASS",
  GUARDRAIL_FAIL = "GUARDRAIL_FAIL",
  HANDOFF = "HANDOFF",
  WAITING = "WAITING",
  MESSAGE = "MESSAGE",
  ERROR = "ERROR",
  DONE = "DONE",
  CONTEXT_CONDENSED = "CONTEXT_CONDENSED",
}

export enum AgentStatus {
  COMPLETED = "COMPLETED",
  FAILED = "FAILED",
  RUNNING = "RUNNING",
  WAITING = "WAITING",
}

export enum FinishReason {
  STOP = "stop",
  HANDOFF = "handoff",
  ERROR = "error",
  MAX_TURNS = "max_turns",
  WAITING = "waiting",
}

export enum AgentStrategy {
  SINGLE = "single",
  HANDOFF = "handoff",
  SEQUENTIAL = "sequential",
  PARALLEL = "parallel",
  ROUTER = "router",
}

/**
 * A timeline item can be lifecycle work around the agent's conversational
 * turns.  Preparation and finalization remain inspectable, but do not inflate
 * the agent-turn count or shift the numbered turns shown to users.
 */
export enum AgentTimelineKind {
  PREPARATION = "PREPARATION",
  TURN = "TURN",
  FINALIZATION = "FINALIZATION",
}

export interface TokenUsage {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
}

export interface TaskAttempt {
  taskId: string;
  retryCount: number;
  status: string;
  startTime?: number;
  endTime?: number;
  durationMs: number;
  workerId?: string;
  reasonForIncompletion?: string;
  inputData?: Record<string, unknown>;
  outputData?: Record<string, unknown>;
}

export interface AgentEvent {
  id: string;
  type: EventType;
  timestamp: number;
  /** Display summary text */
  summary: string;
  /** Full detail - JSON object or text */
  detail?: unknown;
  /** For TOOL_CALL: tool name; for LLM: model name */
  toolName?: string;
  /** For LLM: custom base URL override (for debugging) */
  baseUrl?: string;
  /** For TOOL_CALL: tool arguments */
  toolArgs?: Record<string, unknown>;
  /** Explicit fork/join group for concurrently executed tool calls. */
  parallelGroup?: string;
  /** For HANDOFF: target agent name */
  targetAgent?: string;
  /** For ERROR: error message */
  errorMessage?: string;
  /** Duration of this event in ms */
  durationMs?: number;
  /** Whether the event represents a success (for TOOL_RESULT, GUARDRAIL) */
  success?: boolean;
  tokens?: TokenUsage;
  /** Combined tool result — when set, EventRow renders Input + Output sections */
  result?: unknown;
  /** For CONTEXT_CONDENSED: condensation stats */
  condensationInfo?: {
    trigger: string;
    messagesBefore: number;
    messagesAfter: number;
    exchangesCondensed: number;
  };
  /** Conductor task execution metadata (start/end/schedule times, worker, etc.) */
  taskMeta?: {
    taskId?: string;
    taskType?: string;
    referenceTaskName?: string;
    scheduledTime?: number;
    startTime?: number;
    endTime?: number;
    workerId?: string;
    reasonForIncompletion?: string;
    retryCount?: number;
    /** Total execution attempts (original + retries). Present when > 1. */
    totalAttempts?: number;
    /** All task attempts (original + retries) — present when totalAttempts > 1 */
    allAttempts?: TaskAttempt[];
    pollCount?: number;
    seq?: string;
    queueWaitTime?: number;
  };
}

export interface AgentTurn {
  /** Stable selection key. Legacy/mock turns may omit this. */
  id?: string;
  /** Defaults to TURN for legacy/mock data. */
  kind?: AgentTimelineKind;
  turnNumber: number;
  events: AgentEvent[];
  status: AgentStatus;
  durationMs: number;
  tokens: TokenUsage;
  /** Sub-agent runs spawned from this turn (handoff, parallel, etc.) */
  subAgents: AgentRunData[];
  /** How sub-agents were spawned */
  strategy?: AgentStrategy;
}

export interface AgentRunData {
  id: string;
  agentName: string;
  /** Runtime/protocol that executes this agent, for example `conductor` or `a2a`. */
  agentType?: string;
  model?: string;
  turns: AgentTurn[];
  status: AgentStatus;
  totalTokens: TokenUsage;
  totalDurationMs: number;
  finishReason?: FinishReason;
  strategy?: AgentStrategy;
  /** The parent's orchestration mode for this particular invocation. */
  invocationStrategy?: AgentStrategy;
  /** Conductor sub-workflow ID — present when this run can be fetched for full details */
  subWorkflowId?: string;
  /** Initial input given to this agent. May be structured workflow data. */
  input?: unknown;
  /** Final output from this agent. May be structured workflow data. */
  output?: unknown;
  /** Failure reason if status is FAILED */
  failureReason?: string;
  /** Agent definition from workflow.definition.metadata.agentDef */
  agentDef?: Record<string, unknown>;
}

export interface ExecutionMetrics {
  totalAgents: number;
  totalTurns: number;
  totalTokens: TokenUsage;
  totalDurationMs: number;
  failedAgents: number;
  waitingAgents: number;
}
