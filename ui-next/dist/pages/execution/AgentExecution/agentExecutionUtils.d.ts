import { AgentRunData, AgentStatus, AgentTimelineKind, AgentTurn, ExecutionMetrics } from "./types";
import { WorkflowExecution } from "types/Execution";
/** Map a model name to its provider icon path in /integrations-icons/ */
export declare function getModelIconPath(model: string | undefined): string | null;
/** Compute aggregate metrics recursively across all agents */
export declare function computeMetrics(run: AgentRunData): ExecutionMetrics;
/** Format duration in ms to a human-readable string */
export declare function formatDuration(ms: number): string;
/** Format token count for display */
export declare function formatTokens(count: number): string;
export declare function timelineItemId(turn: AgentTurn): string;
export declare function timelineItemKind(turn: AgentTurn): AgentTimelineKind;
export declare function timelineItemLabel(turn: AgentTurn): string;
/** Compact, safe text for diagram and tree labels when an execution carries JSON input/output. */
export declare function agentValuePreview(value: unknown, maxLength?: number): string | undefined;
/**
 * Whether a task status represents a terminal failure. Use this instead of
 * comparing against the literal "FAILED" — Conductor has several terminal
 * failure statuses (FAILED_WITH_TERMINAL_ERROR, TIMED_OUT, CANCELED) and
 * treating only "FAILED" as failed leaves the others rendering as running.
 */
export declare function isFailedTaskStatus(status: string): boolean;
/** Maps task status to a tri-state success flag: true=completed, false=failed, undefined=in-progress */
export declare function taskSuccess(status: string): boolean | undefined;
export declare function mapTaskStatus(status: string): AgentStatus;
/**
 * Recursively locate the sub-agent run with the given id anywhere in the tree
 * and return a new tree with that node replaced by `updater(node)`. Used to
 * splice a freshly-fetched sub-agent's real turns/subAgents into the existing
 * tree in place when the user expands a collapsed node (issue #1452), without
 * navigating away like "drill in" does.
 *
 * Returns the original reference untouched when `targetId` isn't found so
 * callers can no-op cheaply (e.g. React state updates that don't need to
 * re-render when nothing changed).
 */
export declare function replaceAgentRunNode(root: AgentRunData, targetId: string, updater: (node: AgentRunData) => AgentRunData): AgentRunData;
/**
 * Transform a top-level WorkflowExecution into AgentRunData for the Agent Execution tab.
 * Groups tasks by DO_WHILE iteration; each iteration becomes one turn.
 * Each handoff SUB_WORKFLOW task within an iteration becomes a sub-agent.
 */
export declare function transformWorkflowExecutionToAgentRun(execution: WorkflowExecution): AgentRunData;
