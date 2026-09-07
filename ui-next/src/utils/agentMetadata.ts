import {
  A2AAgentCard,
  AgentMetadataSnapshot,
  AgentRuntimeType,
  AgentTaskInput,
  TaskDef,
  TaskType,
  WorkflowDef,
} from "types";

export const AGENT_SNAPSHOT_SCHEMA_VERSION = 1 as const;

type JsonFetcher = (
  path: string,
  options?: { method?: string; body?: string },
) => Promise<unknown>;

type AgentWorkflowDefinition = {
  name?: string;
  version?: number;
  metadata?: {
    agent_sdk?: unknown;
    normalizedAgentDef?: unknown;
    agentDef?: unknown;
  };
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
  value != null && typeof value === "object" && !Array.isArray(value);

export const isDynamicAgentIdentity = (value: unknown): value is string =>
  typeof value === "string" && value.includes("${");

export const agentRuntimeType = (input: unknown): AgentRuntimeType =>
  isRecord(input) && input.agentType === "conductor" ? "conductor" : "a2a";

export const agentSourceIdentity = (input: unknown): string => {
  if (!isRecord(input)) return "";
  const identity =
    agentRuntimeType(input) === "conductor" ? input.name : input.agentUrl;
  return String(identity ?? "").trim();
};

export const agentSourceKey = (input: unknown): string => {
  if (!isRecord(input)) return "a2a||";
  const type = agentRuntimeType(input);
  const identity = agentSourceIdentity(input);
  const version = type === "conductor" ? String(input.version ?? "latest") : "";
  return `${type}|${identity}|${version}`;
};

export const createUnresolvedAgentSnapshot = (
  input: unknown,
): AgentMetadataSnapshot => {
  const type = agentRuntimeType(input);
  const identity = agentSourceIdentity(input);
  const dynamic = isDynamicAgentIdentity(identity);
  const record = isRecord(input) ? input : {};
  const requestedVersion =
    type === "conductor" && typeof record.version === "number"
      ? record.version
      : undefined;

  return {
    schemaVersion: AGENT_SNAPSHOT_SCHEMA_VERSION,
    agentType: type,
    displayName:
      identity || (type === "conductor" ? "Conductor agent" : "A2A agent"),
    source: {
      ...(type === "conductor"
        ? { name: identity, requestedVersion }
        : { url: identity }),
      ...(dynamic ? { expression: identity } : {}),
    },
    resolved: false,
    ...(type === "conductor"
      ? {
          conductor: {
            name: identity,
            requestedVersion,
          },
        }
      : { a2a: { url: identity } }),
  };
};

export const buildConductorAgentSnapshot = (
  input: AgentTaskInput,
  definition: AgentWorkflowDefinition,
): AgentMetadataSnapshot => {
  const name = agentSourceIdentity(input);
  const requestedVersion =
    "version" in input && typeof input.version === "number"
      ? input.version
      : undefined;
  const normalized = definition.metadata?.normalizedAgentDef;
  const raw = definition.metadata?.agentDef;
  const agentConfig = isRecord(normalized)
    ? normalized
    : isRecord(raw)
      ? raw
      : undefined;

  return {
    schemaVersion: AGENT_SNAPSHOT_SCHEMA_VERSION,
    agentType: "conductor",
    displayName:
      (agentConfig?.name as string | undefined) || definition.name || name,
    source: { name, requestedVersion },
    resolved: true,
    conductor: {
      name,
      requestedVersion,
      resolvedVersion: definition.version,
      framework:
        typeof definition.metadata?.agent_sdk === "string"
          ? definition.metadata.agent_sdk
          : undefined,
      normalization: isRecord(normalized) ? "normalized" : "raw",
      agentConfig,
    },
  };
};

export const buildA2AAgentSnapshot = (
  input: AgentTaskInput,
  card: A2AAgentCard,
): AgentMetadataSnapshot => {
  const url = agentSourceIdentity(input);
  return {
    schemaVersion: AGENT_SNAPSHOT_SCHEMA_VERSION,
    agentType: "a2a",
    displayName: card.name || url,
    source: { url },
    resolved: true,
    a2a: { url, agentCard: card },
  };
};

export const getAgentSnapshot = (
  task: Pick<TaskDef, "metadata"> | undefined,
): AgentMetadataSnapshot | undefined => task?.metadata?.agent;

export const isAgentSnapshotCurrent = (
  snapshot: AgentMetadataSnapshot | undefined,
  input: unknown,
): boolean => {
  if (!snapshot || snapshot.schemaVersion !== AGENT_SNAPSHOT_SCHEMA_VERSION) {
    return false;
  }
  if (snapshot.agentType !== agentRuntimeType(input)) return false;
  if (snapshot.agentType === "conductor") {
    const record = isRecord(input) ? input : {};
    return (
      snapshot.source.name === agentSourceIdentity(input) &&
      snapshot.source.requestedVersion ===
        (typeof record.version === "number" ? record.version : undefined)
    );
  }
  return snapshot.source.url === agentSourceIdentity(input);
};

export const withAgentSnapshot = <
  T extends { metadata?: Record<string, unknown> },
>(
  task: T,
  snapshot: AgentMetadataSnapshot,
): T => ({
  ...task,
  metadata: { ...(task.metadata ?? {}), agent: snapshot },
});

export async function resolveAgentSnapshot(
  input: AgentTaskInput,
  fetchJson: JsonFetcher,
): Promise<AgentMetadataSnapshot> {
  const identity = agentSourceIdentity(input);
  if (!identity || isDynamicAgentIdentity(identity)) {
    return createUnresolvedAgentSnapshot(input);
  }

  if (agentRuntimeType(input) === "conductor") {
    const version =
      "version" in input && typeof input.version === "number"
        ? `?version=${encodeURIComponent(input.version)}`
        : "";
    const definition = (await fetchJson(
      `/agent/definitions/${encodeURIComponent(identity)}${version}`,
    )) as AgentWorkflowDefinition;
    return buildConductorAgentSnapshot(input, definition);
  }

  const inputRecord = input as unknown as Record<string, unknown>;
  const cardRequest = {
    agentType: "a2a",
    agentUrl: identity,
    ...(isRecord(inputRecord.headers) ? { headers: inputRecord.headers } : {}),
  };
  const response = (await fetchJson("/a2a/agent-card", {
    method: "POST",
    body: JSON.stringify(cardRequest),
  })) as { agentCard?: A2AAgentCard };
  if (!response?.agentCard) {
    throw new Error("Agent Card response did not contain agentCard");
  }
  return buildA2AAgentSnapshot(input, response.agentCard);
}

const childTaskLists = (task: TaskDef): TaskDef[][] => {
  const lists: TaskDef[][] = [];
  if (task.decisionCases) lists.push(...Object.values(task.decisionCases));
  if (task.defaultCase) lists.push(task.defaultCase);
  if (task.forkTasks) lists.push(...task.forkTasks);
  if (task.loopOver) lists.push(task.loopOver);
  return lists;
};

/**
 * Refresh stale static AGENT snapshots immediately before registration. Resolution is best effort:
 * a failed remote discovery leaves an explicit unresolved snapshot and never blocks workflow save.
 */
export async function resolveAgentSnapshotsInWorkflow(
  workflow: WorkflowDef,
  fetchJson: JsonFetcher,
): Promise<WorkflowDef> {
  const cloned = JSON.parse(JSON.stringify(workflow)) as WorkflowDef;

  const visit = async (tasks: TaskDef[]) => {
    for (const task of tasks) {
      if (task.type === TaskType.AGENT) {
        const input = (task.inputParameters ?? {}) as AgentTaskInput;
        const current = getAgentSnapshot(task);
        if (!isAgentSnapshotCurrent(current, input) || !current?.resolved) {
          try {
            task.metadata = {
              ...(task.metadata ?? {}),
              agent: await resolveAgentSnapshot(input, fetchJson),
            };
          } catch {
            task.metadata = {
              ...(task.metadata ?? {}),
              agent: createUnresolvedAgentSnapshot(input),
            };
          }
        }
      }
      for (const children of childTaskLists(task)) await visit(children);
    }
  };

  await visit(cloned.tasks ?? []);
  return cloned;
}

export interface AgentTaskPresentation {
  badge: "A2A AGENT" | "CONDUCTOR AGENT";
  name: string;
  taskReferenceName: string;
  unresolved: boolean;
}

export const getAgentTaskPresentation = (
  task: Pick<TaskDef, "inputParameters" | "metadata" | "taskReferenceName">,
): AgentTaskPresentation => {
  const input = task.inputParameters ?? {};
  const snapshot = getAgentSnapshot(task);
  const type = snapshot?.agentType ?? agentRuntimeType(input);
  const identity = snapshot?.displayName || agentSourceIdentity(input);
  return {
    badge: type === "conductor" ? "CONDUCTOR AGENT" : "A2A AGENT",
    name: identity || (type === "conductor" ? "Conductor agent" : "A2A agent"),
    taskReferenceName: task.taskReferenceName,
    // Conductor agents are registered locally and their configured name is
    // authoritative. A missing editor snapshot only means that the optional
    // detail hydration has not completed; it does not make the agent itself
    // unresolved. A2A identities depend on remote Agent Card discovery.
    unresolved: type === "a2a" && !snapshot?.resolved,
  };
};
