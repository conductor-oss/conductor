import { A2AAgentCard, AgentMetadataSnapshot, AgentRuntimeType, AgentTaskInput, ProviderAgentRuntimeType, TaskDef, WorkflowDef } from "types";
export declare const AGENT_SNAPSHOT_SCHEMA_VERSION: 1;
type JsonFetcher = (path: string, options?: {
    method?: string;
    body?: string;
}) => Promise<unknown>;
type AgentWorkflowDefinition = {
    name?: string;
    version?: number;
    metadata?: {
        agent_sdk?: unknown;
        normalizedAgentDef?: unknown;
        agentDef?: unknown;
    };
};
export declare const isDynamicAgentIdentity: (value: unknown) => value is string;
/** Human label per runtime, for badges and detail rows. */
export declare const AGENT_RUNTIME_LABELS: Record<AgentRuntimeType, string>;
export declare const isProviderRuntime: (type: AgentRuntimeType) => type is ProviderAgentRuntimeType;
/** The current name for an agent type string, carrying a renamed runtime's old name forward. */
export declare const canonicalAgentType: (type?: string | null) => string;
export declare const agentRuntimeType: (input: unknown) => AgentRuntimeType;
export declare const agentSourceIdentity: (input: unknown) => string;
export declare const agentSourceKey: (input: unknown) => string;
export declare const buildProviderAgentSnapshot: (input: AgentTaskInput) => AgentMetadataSnapshot;
export declare const createUnresolvedAgentSnapshot: (input: unknown) => AgentMetadataSnapshot;
export declare const buildConductorAgentSnapshot: (input: AgentTaskInput, definition: AgentWorkflowDefinition) => AgentMetadataSnapshot;
export declare const buildA2AAgentSnapshot: (input: AgentTaskInput, card: A2AAgentCard) => AgentMetadataSnapshot;
export declare const getAgentSnapshot: (task: Pick<TaskDef, "metadata"> | undefined) => AgentMetadataSnapshot | undefined;
export declare const isAgentSnapshotCurrent: (snapshot: AgentMetadataSnapshot | undefined, input: unknown) => boolean;
export declare const withAgentSnapshot: <T extends {
    metadata?: Record<string, unknown>;
}>(task: T, snapshot: AgentMetadataSnapshot) => T;
export declare function resolveAgentSnapshot(input: AgentTaskInput, fetchJson: JsonFetcher): Promise<AgentMetadataSnapshot>;
/**
 * Refresh stale static AGENT snapshots immediately before registration. Resolution is best effort:
 * a failed remote discovery leaves an explicit unresolved snapshot and never blocks workflow save.
 */
export declare function resolveAgentSnapshotsInWorkflow(workflow: WorkflowDef, fetchJson: JsonFetcher): Promise<WorkflowDef>;
/** Uppercase badge shown on the task card, one per runtime. */
export declare const AGENT_RUNTIME_BADGES: Record<AgentRuntimeType, string>;
export interface AgentTaskPresentation {
    badge: string;
    name: string;
    taskReferenceName: string;
}
/**
 * Deliberately reports no "unresolved" state. Resolution only runs through the editor's save flow,
 * so a workflow registered any other way — API, SDK, curl, or a save predating agent snapshots —
 * never has a snapshot, and a resolution attempt can also fail transiently. Neither means the
 * configured agent is broken, so the card shows the configured identity either way.
 */
export declare const getAgentTaskPresentation: (task: Pick<TaskDef, "inputParameters" | "metadata" | "taskReferenceName">) => AgentTaskPresentation;
export {};
