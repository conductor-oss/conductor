import {
  A2AAgentCard,
  AgentMetadataSnapshot,
  AgentRuntimeType,
  AgentTaskInput,
  ProviderAgentRuntimeType,
  ProviderAgentSnapshot,
  TaskDef,
  TaskType,
  WorkflowDef,
} from "types";
import { detectAuthMethod } from "pages/definition/EditorPanel/TaskFormTab/forms/agent/agentAuthMethods";

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

/** Hosted-platform runtimes, keyed by the platform's own identifier in `rawConfig`. */
const PROVIDER_RUNTIMES: Record<ProviderAgentRuntimeType, string> = {
  bedrock: "agentId",
  "azure-foundry": "assistantId",
  "openai-assistants": "assistantId",
};

const KNOWN_RUNTIMES: AgentRuntimeType[] = [
  "a2a",
  "conductor",
  ...(Object.keys(PROVIDER_RUNTIMES) as ProviderAgentRuntimeType[]),
];

/** Human label per runtime, for badges and detail rows. */
export const AGENT_RUNTIME_LABELS: Record<AgentRuntimeType, string> = {
  a2a: "A2A",
  conductor: "Conductor",
  bedrock: "Bedrock",
  "azure-foundry": "Azure AI Foundry",
  "openai-assistants": "OpenAI Assistants",
};

export const isProviderRuntime = (
  type: AgentRuntimeType,
): type is ProviderAgentRuntimeType => type in PROVIDER_RUNTIMES;

export const agentRuntimeType = (input: unknown): AgentRuntimeType => {
  if (!isRecord(input)) return "a2a";
  const declared = String(input.agentType ?? "")
    .trim()
    .toLowerCase();
  return (
    KNOWN_RUNTIMES.find((runtime) => runtime === declared) ??
    // Absent or unrecognized falls back to A2A, matching the server's default.
    "a2a"
  );
};

const rawConfigValue = (input: unknown, key: string): string | undefined => {
  if (!isRecord(input) || !isRecord(input.rawConfig)) return undefined;
  const value = input.rawConfig[key];
  return value == null ? undefined : String(value).trim() || undefined;
};

export const agentSourceIdentity = (input: unknown): string => {
  if (!isRecord(input)) return "";
  const type = agentRuntimeType(input);
  if (isProviderRuntime(type)) {
    // Foundry accepts agentId as an alias for assistantId, so try both keys.
    return (
      rawConfigValue(input, PROVIDER_RUNTIMES[type]) ??
      rawConfigValue(input, "assistantId") ??
      rawConfigValue(input, "agentId") ??
      ""
    );
  }
  const identity = type === "conductor" ? input.name : input.agentUrl;
  return String(identity ?? "").trim();
};

const providerSnapshotFrom = (
  input: unknown,
  identity: string,
): ProviderAgentSnapshot => ({
  agentId: identity,
  // The method, not the credential — a snapshot describes the agent, and the values themselves are
  // secret references the engine resolves at run time.
  authMethod: isRecord(input)
    ? detectAuthMethod(
        agentRuntimeType(input),
        input.credentials as Record<string, unknown> | undefined,
      )?.label
    : undefined,
  endpoint: rawConfigValue(input, "endpoint"),
  region: rawConfigValue(input, "region"),
  apiVersion: rawConfigValue(input, "apiVersion"),
});

export const agentSourceKey = (input: unknown): string => {
  if (!isRecord(input)) return "a2a||";
  const type = agentRuntimeType(input);
  const identity = agentSourceIdentity(input);
  const version = type === "conductor" ? String(input.version ?? "latest") : "";
  return `${type}|${identity}|${version}`;
};

export const buildProviderAgentSnapshot = (
  input: AgentTaskInput,
): AgentMetadataSnapshot => {
  const type = agentRuntimeType(input) as ProviderAgentRuntimeType;
  const identity = agentSourceIdentity(input);
  return {
    schemaVersion: AGENT_SNAPSHOT_SCHEMA_VERSION,
    agentType: type,
    displayName: identity || `${AGENT_RUNTIME_LABELS[type]} agent`,
    source: { name: identity },
    // Nothing to discover: a hosted agent is fully named by the task input.
    resolved: Boolean(identity),
    provider: providerSnapshotFrom(input, identity),
  };
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
    displayName: identity || `${AGENT_RUNTIME_LABELS[type]} agent`,
    source: {
      ...(type === "conductor"
        ? { name: identity, requestedVersion }
        : isProviderRuntime(type)
          ? { name: identity }
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
      : isProviderRuntime(type)
        ? { provider: providerSnapshotFrom(input, identity) }
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
  if (isProviderRuntime(snapshot.agentType)) {
    return snapshot.provider?.agentId === agentSourceIdentity(input);
  }
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

  // A hosted agent needs no lookup — and must not be sent to A2A card discovery, which would
  // fail on the missing agentUrl and leave the task looking unresolved.
  if (isProviderRuntime(agentRuntimeType(input))) {
    return buildProviderAgentSnapshot(input);
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

/** Uppercase badge shown on the task card, one per runtime. */
export const AGENT_RUNTIME_BADGES: Record<AgentRuntimeType, string> = {
  a2a: "A2A AGENT",
  conductor: "CONDUCTOR AGENT",
  bedrock: "BEDROCK AGENT",
  "azure-foundry": "AZURE FOUNDRY AGENT",
  "openai-assistants": "OPENAI AGENT",
};

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
export const getAgentTaskPresentation = (
  task: Pick<TaskDef, "inputParameters" | "metadata" | "taskReferenceName">,
): AgentTaskPresentation => {
  const input = task.inputParameters ?? {};
  // The live input is authoritative: a stored snapshot lags a live edit, since resolution only runs
  // on save. Reuse the snapshot's display name only while its type still matches what is configured.
  const type = agentRuntimeType(input);
  const snapshot = getAgentSnapshot(task);
  const identity =
    (snapshot?.agentType === type ? snapshot.displayName : undefined) ||
    agentSourceIdentity(input);
  return {
    badge: AGENT_RUNTIME_BADGES[type],
    name: identity || `${AGENT_RUNTIME_LABELS[type]} agent`,
    taskReferenceName: task.taskReferenceName,
  };
};
