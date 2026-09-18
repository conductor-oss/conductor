import { Box, Chip, Divider, Paper, Typography } from "@mui/material";
import { ReactNode, useMemo } from "react";
import { A2AAgentCard, A2AAgentExtension, AgentMetadataSnapshot } from "types";
import { toolCategoryForPanel } from "utils/agentTaskCategory";

const asRecord = (value: unknown): Record<string, unknown> | undefined =>
  value != null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined;

const asArray = (value: unknown): unknown[] =>
  Array.isArray(value) ? value : [];

const firstDefined = (
  record: Record<string, unknown>,
  ...keys: string[]
): unknown => keys.map((key) => record[key]).find((value) => value != null);

const itemName = (value: unknown, fallback = "[item]"): string => {
  if (typeof value === "string") return value;
  const record = asRecord(value);
  const name =
    record?.name ?? record?._worker_ref ?? asRecord(record?.function)?.name;
  return typeof name === "string" && name ? name : fallback;
};

function DetailRow({ label, value }: { label: string; value: ReactNode }) {
  if (value == null || value === "") return null;
  return (
    <Box
      sx={{
        display: "grid",
        gridTemplateColumns: "minmax(110px, 32%) minmax(0, 1fr)",
        gap: 1.5,
        px: 2,
        py: 1,
        borderBottom: "1px solid",
        borderColor: "divider",
        "&:last-child": { borderBottom: 0 },
      }}
    >
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Box sx={{ minWidth: 0, wordBreak: "break-word", fontSize: "0.875rem" }}>
        {value}
      </Box>
    </Box>
  );
}

function TagList({ values }: { values: unknown[] }) {
  return (
    <Box
      sx={{
        display: "flex",
        flexWrap: "wrap",
        gap: 0.5,
        maxHeight: 180,
        overflowY: "auto",
      }}
    >
      {values.map((value, index) => (
        <Chip key={index} label={itemName(value)} size="small" />
      ))}
    </Box>
  );
}

function LongText({ value }: { value: unknown }) {
  return (
    <Box
      component="pre"
      sx={{
        m: 0,
        font: "inherit",
        fontSize: "0.8rem",
        lineHeight: 1.55,
        whiteSpace: "pre-wrap",
        wordBreak: "break-word",
        maxHeight: 260,
        overflowY: "auto",
      }}
    >
      {typeof value === "string" ? value : JSON.stringify(value, null, 2)}
    </Box>
  );
}

const guardrailNames = (value: unknown): string[] =>
  asArray(value).map((entry) => {
    const record = asRecord(entry);
    return itemName(
      record?.guardrail_function ?? record?.guardrailFunction ?? entry,
    );
  });

function ToolGroup({ label, tools }: { label: string; tools: unknown[] }) {
  if (!tools.length) return null;
  return (
    <DetailRow
      label={`${label} (${tools.length})`}
      value={
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            gap: 0.75,
            maxHeight: 420,
            overflowY: "auto",
          }}
        >
          {tools.map((tool, index) => {
            const record = asRecord(tool) ?? {};
            const config = asRecord(record.config) ?? {};
            const description = firstDefined(record, "description");
            const toolGuardrails = [
              ...guardrailNames(firstDefined(record, "guardrails")),
              ...guardrailNames(
                firstDefined(record, "inputGuardrails", "input_guardrails"),
              ),
              ...guardrailNames(
                firstDefined(record, "outputGuardrails", "output_guardrails"),
              ),
              ...guardrailNames(firstDefined(config, "guardrails")),
            ];
            return (
              <Paper key={index} variant="outlined" sx={{ p: 1 }}>
                <Typography sx={{ fontSize: "0.8rem", fontWeight: 650 }}>
                  {itemName(tool)}
                </Typography>
                {typeof description === "string" && (
                  <Typography variant="caption" color="text.secondary">
                    {description}
                  </Typography>
                )}
                {toolGuardrails.length > 0 && (
                  <Box sx={{ mt: 0.75 }}>
                    <Typography variant="caption" color="text.secondary">
                      Guardrails
                    </Typography>
                    <TagList values={toolGuardrails} />
                  </Box>
                )}
              </Paper>
            );
          })}
        </Box>
      }
    />
  );
}

export function AgentDefinitionDetails({
  agentDef,
  title = "Configuration",
}: {
  agentDef: Record<string, unknown>;
  title?: string;
}) {
  const tools = asArray(agentDef.tools);
  const toolGroups = useMemo(() => {
    const groups: Record<string, unknown[]> = {
      Worker: [],
      HTTP: [],
      MCP: [],
      RAG: [],
      Guardrail: [],
      "Sub-agent": [],
    };
    tools.forEach((tool) => {
      const category = toolCategoryForPanel(tool as Record<string, unknown>);
      const label =
        category === "http"
          ? "HTTP"
          : category === "mcp"
            ? "MCP"
            : category === "rag"
              ? "RAG"
              : category === "guardrail"
                ? "Guardrail"
                : category === "agent"
                  ? "Sub-agent"
                  : "Worker";
      groups[label].push(tool);
    });
    return groups;
  }, [tools]);

  const instructions = firstDefined(agentDef, "instructions", "prompt");
  const inputGuardrails = guardrailNames(
    firstDefined(agentDef, "inputGuardrails", "input_guardrails"),
  );
  const outputGuardrails = guardrailNames(
    firstDefined(agentDef, "outputGuardrails", "output_guardrails"),
  );
  const guardrails = guardrailNames(agentDef.guardrails);
  const subAgents = asArray(agentDef.agents);
  const runtimeRows: Array<[string, unknown]> = [
    ["Strategy", agentDef.strategy],
    ["Maximum turns", firstDefined(agentDef, "maxTurns", "max_turns")],
    ["Maximum tokens", firstDefined(agentDef, "maxTokens", "max_tokens")],
    ["Temperature", agentDef.temperature],
    [
      "Reasoning effort",
      firstDefined(agentDef, "reasoningEffort", "reasoning_effort"),
    ],
    ["Thinking", firstDefined(agentDef, "thinkingConfig", "thinking_config")],
    ["Memory", agentDef.memory],
    ["Output type", firstDefined(agentDef, "outputType", "output_type")],
    [
      "Timeout seconds",
      firstDefined(agentDef, "timeoutSeconds", "timeout_seconds"),
    ],
    ["Planning", firstDefined(agentDef, "enablePlanning", "enable_planning")],
    [
      "Required tools",
      firstDefined(agentDef, "requiredTools", "required_tools"),
    ],
    ["Handoffs", agentDef.handoffs],
    ["Callbacks", agentDef.callbacks],
    [
      "Code execution",
      firstDefined(agentDef, "codeExecution", "code_execution"),
    ],
    ["CLI", firstDefined(agentDef, "cliConfig", "cli_config")],
  ];

  return (
    <Paper variant="outlined" sx={{ overflow: "hidden" }}>
      <Typography
        sx={{
          px: 2,
          py: 1.25,
          fontWeight: 700,
          backgroundColor: "action.hover",
        }}
      >
        {title}
      </Typography>
      <DetailRow label="Name" value={String(agentDef.name ?? "")} />
      <DetailRow label="Model" value={String(agentDef.model ?? "")} />
      {instructions != null && (
        <DetailRow
          label="Instructions"
          value={<LongText value={instructions} />}
        />
      )}
      {(inputGuardrails.length > 0 || outputGuardrails.length > 0) && (
        <DetailRow
          label="Guardrail pipeline"
          value={
            <Box sx={{ display: "flex", flexDirection: "column", gap: 0.75 }}>
              {inputGuardrails.length > 0 && (
                <TagList
                  values={inputGuardrails.map((name) => `Input: ${name}`)}
                />
              )}
              {outputGuardrails.length > 0 && (
                <TagList
                  values={outputGuardrails.map((name) => `Output: ${name}`)}
                />
              )}
            </Box>
          }
        />
      )}
      {guardrails.length > 0 && (
        <DetailRow label="Guardrails" value={<TagList values={guardrails} />} />
      )}
      {Object.entries(toolGroups).map(([label, entries]) => (
        <ToolGroup key={label} label={label} tools={entries} />
      ))}
      {subAgents.length > 0 && (
        <DetailRow
          label={`Sub-agents (${subAgents.length})`}
          value={<TagList values={subAgents} />}
        />
      )}
      {runtimeRows.map(([label, value]) =>
        value == null ||
        value === "" ||
        (Array.isArray(value) && value.length === 0) ? null : (
          <DetailRow
            key={label}
            label={label}
            value={
              typeof value === "string" ||
              typeof value === "number" ||
              typeof value === "boolean" ? (
                String(value)
              ) : (
                <LongText value={value} />
              )
            }
          />
        ),
      )}
    </Paper>
  );
}

const extensionAdvertisedConfig = (
  extensions: A2AAgentExtension[],
): Record<string, unknown> | undefined => {
  const advertised: Record<string, unknown> = {};
  const allowed = [
    "name",
    "model",
    "prompt",
    "instructions",
    "tools",
    "guardrails",
    "inputGuardrails",
    "input_guardrails",
    "outputGuardrails",
    "output_guardrails",
    "agents",
    "strategy",
    "maxTurns",
    "max_turns",
    "reasoningEffort",
    "reasoning_effort",
  ];
  extensions.forEach((extension) => {
    const params = extension.params ?? {};
    const nestedConfig =
      asRecord(params.agentConfig) ?? asRecord(params.agentDefinition) ?? {};
    allowed.forEach((key) => {
      const value = params[key] ?? nestedConfig[key];
      if (value != null) advertised[key] = value;
    });
  });
  return Object.keys(advertised).length ? advertised : undefined;
};

function A2AAgentCardDetails({ card }: { card: A2AAgentCard }) {
  const interfaces = [
    ...(card.supportedInterfaces ?? []),
    ...(card.additionalInterfaces ?? []),
  ];
  const capabilities = card.capabilities ?? {};
  const extensions = capabilities.extensions ?? [];
  const advertisedConfig = extensionAdvertisedConfig(extensions);
  const extendedCard =
    capabilities.extendedAgentCard ||
    card.supportsExtendedAgentCard ||
    card.supportsAuthenticatedExtendedCard;

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
      <Paper variant="outlined" sx={{ overflow: "hidden" }}>
        <Typography
          sx={{
            px: 2,
            py: 1.25,
            fontWeight: 700,
            backgroundColor: "action.hover",
          }}
        >
          Protocol & capabilities
        </Typography>
        <DetailRow label="Agent URL" value={card.url} />
        <DetailRow label="Documentation" value={card.documentationUrl} />
        <DetailRow label="Icon" value={card.iconUrl} />
        <DetailRow label="Provider" value={card.provider?.organization} />
        <DetailRow label="Provider URL" value={card.provider?.url} />
        <DetailRow label="Protocol" value={card.protocolVersion} />
        <DetailRow
          label="Preferred transport"
          value={card.preferredTransport}
        />
        {interfaces.length > 0 && (
          <DetailRow
            label={`Interfaces (${interfaces.length})`}
            value={
              <Box sx={{ display: "flex", flexDirection: "column", gap: 0.75 }}>
                {interfaces.map((agentInterface, index) => (
                  <Paper key={index} variant="outlined" sx={{ p: 1 }}>
                    <Typography
                      sx={{
                        fontSize: "0.8rem",
                        fontWeight: 600,
                        wordBreak: "break-all",
                      }}
                    >
                      {agentInterface.url}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {agentInterface.protocolBinding ??
                        agentInterface.transport}
                      {agentInterface.protocolVersion
                        ? ` · ${agentInterface.protocolVersion}`
                        : ""}
                      {agentInterface.tenant
                        ? ` · tenant ${agentInterface.tenant}`
                        : ""}
                    </Typography>
                  </Paper>
                ))}
              </Box>
            }
          />
        )}
        <DetailRow
          label="Input modes"
          value={<TagList values={card.defaultInputModes ?? []} />}
        />
        <DetailRow
          label="Output modes"
          value={<TagList values={card.defaultOutputModes ?? []} />}
        />
        <DetailRow
          label="Capabilities"
          value={
            <TagList
              values={[
                ...(capabilities.streaming ? ["Streaming"] : []),
                ...(capabilities.pushNotifications
                  ? ["Push notifications"]
                  : []),
                ...(capabilities.stateTransitionHistory
                  ? ["State history"]
                  : []),
                ...(extendedCard ? ["Extended card"] : []),
              ]}
            />
          }
        />
        {(card.securityRequirements?.length || card.security?.length) && (
          <DetailRow
            label="Security requirements"
            value={
              <LongText value={card.securityRequirements ?? card.security} />
            }
          />
        )}
        {card.securitySchemes && (
          <DetailRow
            label="Security schemes"
            value={<LongText value={card.securitySchemes} />}
          />
        )}
        {card.signatures?.length ? (
          <DetailRow
            label="Card signatures"
            value={<LongText value={card.signatures} />}
          />
        ) : null}
      </Paper>

      {(card.skills?.length ?? 0) > 0 && (
        <Paper variant="outlined" sx={{ overflow: "hidden" }}>
          <Typography
            sx={{
              px: 2,
              py: 1.25,
              fontWeight: 700,
              backgroundColor: "action.hover",
            }}
          >
            Skills ({card.skills?.length})
          </Typography>
          <Box
            sx={{
              p: 1.5,
              display: "flex",
              flexDirection: "column",
              gap: 1,
              maxHeight: 520,
              overflowY: "auto",
            }}
          >
            {card.skills?.map((skill, index) => (
              <Paper
                key={skill.id ?? index}
                variant="outlined"
                sx={{ p: 1.25 }}
              >
                <Typography sx={{ fontWeight: 650 }}>
                  {skill.name ?? skill.id}
                </Typography>
                {skill.description && (
                  <Typography variant="body2" color="text.secondary">
                    {skill.description}
                  </Typography>
                )}
                {skill.tags?.length ? (
                  <Box sx={{ mt: 0.75 }}>
                    <TagList values={skill.tags} />
                  </Box>
                ) : null}
                {skill.examples?.length ? (
                  <Box sx={{ mt: 1 }}>
                    <Typography variant="caption" color="text.secondary">
                      Examples
                    </Typography>
                    <LongText value={skill.examples} />
                  </Box>
                ) : null}
                {skill.securityRequirements?.length ? (
                  <Box sx={{ mt: 1 }}>
                    <Typography variant="caption" color="text.secondary">
                      Security
                    </Typography>
                    <LongText value={skill.securityRequirements} />
                  </Box>
                ) : null}
              </Paper>
            ))}
          </Box>
        </Paper>
      )}

      {extensions.length > 0 && (
        <Paper variant="outlined" sx={{ overflow: "hidden" }}>
          <Typography
            sx={{
              px: 2,
              py: 1.25,
              fontWeight: 700,
              backgroundColor: "action.hover",
            }}
          >
            Declared extensions ({extensions.length})
          </Typography>
          {extensions.map((extension, index) => (
            <Box key={extension.uri ?? index} sx={{ px: 2, py: 1.25 }}>
              {index > 0 && <Divider sx={{ mb: 1.25 }} />}
              <Typography
                sx={{
                  fontSize: "0.8rem",
                  fontWeight: 650,
                  wordBreak: "break-all",
                }}
              >
                {extension.uri || "Unnamed extension"}
              </Typography>
              {extension.description && (
                <Typography variant="body2" color="text.secondary">
                  {extension.description}
                </Typography>
              )}
              {extension.required && (
                <Chip size="small" label="Required" sx={{ mt: 0.75 }} />
              )}
              {extension.params && (
                <Box sx={{ mt: 1 }}>
                  <LongText value={extension.params} />
                </Box>
              )}
            </Box>
          ))}
        </Paper>
      )}

      {advertisedConfig && (
        <AgentDefinitionDetails
          agentDef={advertisedConfig}
          title="Configuration advertised by extensions"
        />
      )}
    </Box>
  );
}

export function AgentSnapshotDetails({
  snapshot,
}: {
  snapshot?: AgentMetadataSnapshot;
}) {
  if (!snapshot) return null;
  const a2aCard = snapshot.a2a?.agentCard;
  const conductorConfig = snapshot.conductor?.agentConfig;
  const description =
    a2aCard?.description ??
    (typeof conductorConfig?.description === "string"
      ? conductorConfig.description
      : undefined);
  const version = a2aCard?.version ?? snapshot.conductor?.resolvedVersion;

  return (
    <Box
      data-testid="agent-card"
      data-agent-type={snapshot.agentType}
      sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}
    >
      <Paper variant="outlined" sx={{ overflow: "hidden" }}>
        <DetailRow label="Name" value={snapshot.displayName} />
        <DetailRow
          label="Agent type"
          value={snapshot.agentType === "conductor" ? "Conductor" : "A2A"}
        />
        <DetailRow
          label="Details"
          value={snapshot.resolved ? "Loaded" : "Unavailable"}
        />
        <DetailRow label="Description" value={description} />
        <DetailRow label="Version" value={version} />
        <DetailRow
          label="Source"
          value={
            snapshot.source.name ??
            snapshot.source.url ??
            snapshot.source.expression
          }
        />
        {snapshot.conductor?.requestedVersion != null && (
          <DetailRow
            label="Requested version"
            value={snapshot.conductor.requestedVersion}
          />
        )}
        {snapshot.conductor?.resolvedVersion != null && (
          <DetailRow
            label="Resolved version"
            value={snapshot.conductor.resolvedVersion}
          />
        )}
        <DetailRow label="Framework" value={snapshot.conductor?.framework} />
      </Paper>
      {snapshot.conductor?.agentConfig && (
        <AgentDefinitionDetails agentDef={snapshot.conductor.agentConfig} />
      )}
      {a2aCard && <A2AAgentCardDetails card={a2aCard} />}
    </Box>
  );
}
