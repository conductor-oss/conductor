import { TaskDef, TaskType, WorkflowDef } from "types";
import {
  agentRuntimeType,
  agentSourceIdentity,
  buildA2AAgentSnapshot,
  createUnresolvedAgentSnapshot,
  getAgentTaskPresentation,
  isAgentSnapshotCurrent,
  resolveAgentSnapshot,
  resolveAgentSnapshotsInWorkflow,
} from "./agentMetadata";

describe("agent metadata resolution", () => {
  it("stores the normalized Conductor definition, actual version, and framework", async () => {
    const fetchJson = vi.fn().mockResolvedValue({
      name: "researcher",
      version: 9,
      metadata: {
        agent_sdk: "openai-agents",
        normalizedAgentDef: {
          name: "Research Agent",
          model: "openai/gpt-5",
          instructions: "Investigate the topic",
          tools: [{ name: "search", toolType: "tool" }],
        },
        agentDef: { name: "Raw Agent", model: "raw-model" },
      },
    });

    const snapshot = await resolveAgentSnapshot(
      { agentType: "conductor", name: "researcher" },
      fetchJson,
    );

    expect(fetchJson).toHaveBeenCalledWith("/agent/definitions/researcher");
    expect(snapshot).toMatchObject({
      schemaVersion: 1,
      agentType: "conductor",
      displayName: "Research Agent",
      resolved: true,
      conductor: {
        name: "researcher",
        resolvedVersion: 9,
        framework: "openai-agents",
        normalization: "normalized",
        agentConfig: { model: "openai/gpt-5" },
      },
    });
  });

  it("falls back to the raw Conductor definition when normalization is unavailable", async () => {
    const snapshot = await resolveAgentSnapshot(
      { agentType: "conductor", name: "legacy", version: 3 },
      vi.fn().mockResolvedValue({
        name: "legacy",
        version: 3,
        metadata: { agentDef: { name: "Legacy Agent", model: "legacy" } },
      }),
    );

    expect(snapshot.conductor).toMatchObject({
      requestedVersion: 3,
      resolvedVersion: 3,
      normalization: "raw",
      agentConfig: { model: "legacy" },
    });
  });

  it("uses A2A request headers for discovery without persisting them", async () => {
    const fetchJson = vi.fn().mockResolvedValue({
      agentCard: {
        name: "Travel Agent",
        version: "1.0.0",
        skills: [{ id: "book", name: "Book travel" }],
      },
    });
    const input = {
      agentUrl: " https://agents.example/travel\n",
      headers: { Authorization: "Bearer private-token" },
    };

    const snapshot = await resolveAgentSnapshot(input, fetchJson);

    expect(fetchJson).toHaveBeenCalledWith("/a2a/agent-card", {
      method: "POST",
      body: JSON.stringify({
        agentType: "a2a",
        agentUrl: "https://agents.example/travel",
        headers: input.headers,
      }),
    });
    expect(snapshot.source.url).toBe("https://agents.example/travel");
    expect(snapshot.a2a?.agentCard?.name).toBe("Travel Agent");
    expect(JSON.stringify(snapshot)).not.toContain("private-token");
    expect(JSON.stringify(snapshot)).not.toContain("Authorization");
  });

  it("does not fetch dynamic identities and marks them unresolved", async () => {
    const fetchJson = vi.fn();

    const snapshot = await resolveAgentSnapshot(
      {
        agentType: "conductor",
        name: "${workflow.input.agentName}",
      },
      fetchJson,
    );

    expect(fetchJson).not.toHaveBeenCalled();
    expect(snapshot).toMatchObject({
      resolved: false,
      source: { expression: "${workflow.input.agentName}" },
    });
  });

  it("refreshes stale snapshots before save and keeps failures saveable", async () => {
    const stale = buildA2AAgentSnapshot(
      { agentUrl: "https://old.example" },
      { name: "Old Agent" },
    );
    const workflow = {
      tasks: [
        {
          name: "agent",
          taskReferenceName: "agent_ref",
          type: TaskType.AGENT,
          inputParameters: { agentUrl: "https://new.example" },
          metadata: { agent: stale },
        },
      ],
    } as unknown as WorkflowDef;

    const resolved = await resolveAgentSnapshotsInWorkflow(
      workflow,
      vi.fn().mockRejectedValue(new Error("offline")),
    );

    expect(resolved.tasks[0].metadata?.agent).toMatchObject({
      resolved: false,
      source: { url: "https://new.example" },
    });
    expect(workflow.tasks[0].metadata?.agent).toBe(stale);
  });

  it("invalidates source changes and describes resolved and unresolved nodes", () => {
    const input = { agentType: "conductor" as const, name: "researcher" };
    const snapshot = createUnresolvedAgentSnapshot(input);

    expect(isAgentSnapshotCurrent(snapshot, input)).toBe(true);
    expect(
      isAgentSnapshotCurrent(snapshot, {
        agentType: "conductor",
        name: "reviewer",
      }),
    ).toBe(false);

    const unresolvedTask = {
      inputParameters: input,
      taskReferenceName: "agent_ref",
      metadata: { agent: snapshot },
    } as Pick<TaskDef, "inputParameters" | "taskReferenceName" | "metadata">;
    expect(getAgentTaskPresentation(unresolvedTask)).toEqual({
      badge: "CONDUCTOR AGENT",
      name: "researcher",
      taskReferenceName: "agent_ref",
    });

    const resolvedTask = {
      ...unresolvedTask,
      inputParameters: { agentUrl: "https://agent.example" },
      metadata: {
        agent: buildA2AAgentSnapshot(
          { agentUrl: "https://agent.example" },
          { name: "Travel Agent" },
        ),
      },
    };
    expect(getAgentTaskPresentation(resolvedTask)).toMatchObject({
      badge: "A2A AGENT",
      name: "Travel Agent",
    });
  });
});

describe("hosted provider runtimes", () => {
  const foundry = {
    agentType: "azure-foundry" as const,
    prompt: "what is the answer?",
    credentials: {
      client_id: "${workflow.secrets.AZURE_FOUNDRY_CRED.client_id}",
      client_secret: "${workflow.secrets.AZURE_FOUNDRY_CRED.client_secret}",
      tenant_id: "${workflow.secrets.AZURE_FOUNDRY_CRED.tenant_id}",
    },
    rawConfig: {
      endpoint: "https://p.services.ai.azure.com/api/projects/p1",
      assistantId: "asst_abc123",
      apiVersion: "2025-01-01-preview",
    },
  };

  it("recognizes every runtime the server registers", () => {
    expect(agentRuntimeType({ agentType: "azure-foundry" })).toBe(
      "azure-foundry",
    );
    expect(agentRuntimeType({ agentType: "bedrock" })).toBe("bedrock");
    expect(agentRuntimeType({ agentType: "openai-assistants" })).toBe(
      "openai-assistants",
    );
    // Absent or unknown still falls back to A2A, matching the server default.
    expect(agentRuntimeType({})).toBe("a2a");
    expect(agentRuntimeType({ agentType: "not-a-runtime" })).toBe("a2a");
  });

  it("takes its identity from the platform's own id in rawConfig", () => {
    expect(agentSourceIdentity(foundry)).toBe("asst_abc123");
    expect(
      agentSourceIdentity({
        agentType: "bedrock",
        rawConfig: { agentId: "AGENT123", agentAliasId: "ALIAS1" },
      }),
    ).toBe("AGENT123");
    // Foundry accepts agentId as an alias for assistantId.
    expect(
      agentSourceIdentity({
        agentType: "azure-foundry",
        rawConfig: { agentId: "asst_alias" },
      }),
    ).toBe("asst_alias");
  });

  it("resolves without any remote discovery call", async () => {
    const fetchJson = vi.fn();

    const snapshot = await resolveAgentSnapshot(foundry, fetchJson);

    // A hosted agent is fully named by the task input. Sending it to A2A card discovery would
    // fail on the missing agentUrl and leave the task looking unresolved.
    expect(fetchJson).not.toHaveBeenCalled();
    expect(snapshot).toMatchObject({
      schemaVersion: 1,
      agentType: "azure-foundry",
      displayName: "asst_abc123",
      resolved: true,
      provider: {
        agentId: "asst_abc123",
        // The method, never the secret values themselves.
        authMethod: "Service principal",
        endpoint: "https://p.services.ai.azure.com/api/projects/p1",
        apiVersion: "2025-01-01-preview",
      },
    });
  });

  it("labels the task card by runtime instead of calling it an unresolved A2A agent", () => {
    const task = {
      inputParameters: foundry,
      taskReferenceName: "ask_the_analyst",
      metadata: { agent: createUnresolvedAgentSnapshot(foundry) },
    } as Pick<TaskDef, "inputParameters" | "taskReferenceName" | "metadata">;

    expect(getAgentTaskPresentation(task)).toEqual({
      badge: "AZURE FOUNDRY AGENT",
      name: "asst_abc123",
      taskReferenceName: "ask_the_analyst",
    });
  });

  it("invalidates a snapshot when the assistant changes", () => {
    const snapshot = createUnresolvedAgentSnapshot(foundry);

    expect(isAgentSnapshotCurrent(snapshot, foundry)).toBe(true);
    expect(
      isAgentSnapshotCurrent(snapshot, {
        ...foundry,
        rawConfig: { ...foundry.rawConfig, assistantId: "asst_other" },
      }),
    ).toBe(false);
    // A runtime switch is a source change too.
    expect(
      isAgentSnapshotCurrent(snapshot, {
        ...foundry,
        agentType: "openai-assistants",
      }),
    ).toBe(false);
  });
});
