import {
  Alert,
  Box,
  CircularProgress,
  FormControlLabel,
  Grid,
  Switch,
  Typography,
} from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import { AgentSnapshotDetails } from "components/features/agents/AgentSnapshotDetails";
import Button from "components/ui/buttons/MuiButton";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import RadioButtonGroup from "components/ui/inputs/RadioButtonGroup";
import { path as _path } from "lodash/fp";
import { AgentSummary } from "pages/agent/types";
import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AgentTaskInput, TaskDef } from "types";
import {
  agentRuntimeType,
  agentSourceIdentity,
  agentSourceKey,
  createUnresolvedAgentSnapshot,
  getAgentSnapshot,
  isAgentSnapshotCurrent,
  isDynamicAgentIdentity,
  isProviderRuntime,
  resolveAgentSnapshot,
  withAgentSnapshot,
} from "utils/agentMetadata";
import { WORKFLOW_DEFINITION_URL } from "utils/constants/route";
import { updateField } from "utils/fieldHelpers";
import { useAuthHeaders, useFetch } from "utils/query";
import { ConductorAdditionalHeadersBase } from "./HTTPTaskForm/ConductorAdditionalHeaders";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import AgentCredentialsSection from "./agent/AgentCredentialsSection";
import { detectAuthMethod } from "./agent/agentAuthMethods";

const AGENT_TYPES = [
  { value: "a2a", label: "A2A" },
  { value: "conductor", label: "Conductor" },
  { value: "microsoft-foundry", label: "Microsoft Foundry" },
  { value: "bedrock", label: "Bedrock" },
  { value: "openai-assistants", label: "OpenAI Assistants" },
];

type ProviderField = {
  key: string;
  label: string;
  placeholder?: string;
  required?: boolean;
  width?: 6 | 12;
  hint?: string;
  /** Only meaningful when a token is minted — hidden under API key auth, which mints none. */
  tokenOnly?: boolean;
};

/**
 * `rawConfig` keys each hosted runtime reads, taken from its client: AzureFoundryAgentClient,
 * BedrockAgentClient, OpenAiAssistantsAgentClient. Required fields are the ones whose absence makes
 * the client reject the task outright.
 */
const PROVIDER_FIELDS: Record<string, ProviderField[]> = {
  "microsoft-foundry": [
    {
      key: "endpoint",
      label: "Project endpoint",
      placeholder: "https://<project>.services.ai.azure.com/api/projects/<p>",
      required: true,
      width: 12,
    },
    {
      key: "assistantId",
      label: "Assistant ID",
      placeholder: "asst_…",
      required: true,
      width: 6,
    },
    { key: "apiVersion", label: "API version (optional)", width: 6 },
    {
      key: "scope",
      label: "Token scope (optional)",
      width: 12,
      tokenOnly: true,
      placeholder: "Derived from the endpoint",
      hint: "Which Azure resource the token is valid for. Conductor derives it from the endpoint host, so leave this empty unless you are on a sovereign cloud or behind a proxy that hides it.",
    },
  ],
  bedrock: [
    { key: "agentId", label: "Agent ID", required: true, width: 6 },
    { key: "agentAliasId", label: "Agent alias ID", required: true, width: 6 },
    {
      key: "region",
      label: "Region (optional)",
      placeholder: "us-east-1",
      width: 6,
    },
  ],
  "openai-assistants": [
    {
      key: "assistantId",
      label: "Assistant ID",
      placeholder: "asst_…",
      required: true,
      width: 6,
    },
    {
      key: "baseUrl",
      label: "Base URL (optional)",
      placeholder: "https://api.openai.com/v1",
      width: 6,
    },
  ],
};

const jsonOrEmpty = (value: unknown): string => {
  if (value == null || value === "") return "";
  if (typeof value === "string") return value;
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
};

/** Keep whatever was typed while it is still mid-edit; store an object once it parses. */
const parseJsonOrRaw = (value: string): unknown => {
  const trimmed = value.trim();
  if (trimmed === "") return undefined;
  try {
    return JSON.parse(trimmed);
  } catch {
    return value;
  }
};

/**
 * Config form for the AGENT task. Two runtimes, one task type, disjoint input shapes:
 * `agentType: "a2a"` calls a remote Agent2Agent endpoint (poll / streaming / push modes); `"conductor"`
 * runs a registered agent on the embedded agentspan runtime — its input mirrors `POST /api/agent/start`
 * (`AgentStartRequest`), not the A2A message shape.
 */
export const AgentTaskForm = ({ task, onChange }: TaskFormProps) => {
  const get = (p: string) => _path(p, task);
  const set = (p: string, value: any) => onChange(updateField(p, value, task));

  const latestTaskRef = useRef(task);
  const lastAutomaticResolutionRef = useRef<string>();
  const [resolutionWarning, setResolutionWarning] = useState<string>();
  const [isResolving, setIsResolving] = useState(false);
  const fetchContext = useFetchContext();
  const authHeaders = useAuthHeaders();

  useEffect(() => {
    latestTaskRef.current = task;
  }, [task]);

  const agentType = (get("inputParameters.agentType") as string) || "a2a";
  const runtime = agentRuntimeType(task.inputParameters);
  // A2A takes the message in any of message/parts/text/prompt; every other runtime reads prompt
  // and nothing else, which is the rule the server enforces at save time.
  const promptMissing =
    runtime !== "a2a" && !String(task.inputParameters?.prompt ?? "").trim();

  // An API key is sent as a header, so nothing is scoped; every other method mints a token.
  const mintsToken =
    detectAuthMethod(
      runtime,
      task.inputParameters?.credentials as Record<string, unknown> | undefined,
    )?.id !== "apiKey";
  const isConductor = agentType === "conductor";
  const isProvider = isProviderRuntime(runtime);
  // Everything that used to hang off !isConductor is A2A-only — remote URL, streaming, push,
  // headers, message shape — and must not render for a hosted runtime.
  const isA2A = !isConductor && !isProvider;
  const agentName = get("inputParameters.name") as string | undefined;
  const taskInput = (task.inputParameters ?? {}) as AgentTaskInput;
  const sourceKey = agentSourceKey(taskInput);
  const snapshot = getAgentSnapshot(task as Pick<TaskDef, "metadata">);

  const setSource = useCallback(
    (path: string, value: unknown) => {
      const changed = updateField(path, value, task) as Partial<TaskDef>;
      const input = (changed.inputParameters ?? {}) as AgentTaskInput;
      setResolutionWarning(undefined);
      onChange(
        withAgentSnapshot(
          changed as Partial<TaskDef> & { metadata?: Record<string, unknown> },
          createUnresolvedAgentSnapshot(input),
        ),
      );
    },
    [onChange, task],
  );

  const resolveSnapshot = useCallback(
    async (input: AgentTaskInput) => {
      const requestedSourceKey = agentSourceKey(input);
      const identity = agentSourceIdentity(input);
      if (!identity || isDynamicAgentIdentity(identity)) {
        const current = latestTaskRef.current;
        if (agentSourceKey(current.inputParameters) === requestedSourceKey) {
          onChange(
            withAgentSnapshot(
              current as Partial<TaskDef> & {
                metadata?: Record<string, unknown>;
              },
              createUnresolvedAgentSnapshot(input),
            ),
          );
        }
        return;
      }

      setIsResolving(true);
      setResolutionWarning(undefined);
      try {
        const resolved = await resolveAgentSnapshot(input, (path, options) =>
          fetchWithContext(path, fetchContext, {
            method: options?.method ?? "GET",
            headers: {
              "Content-Type": "application/json",
              ...authHeaders,
            },
            body: options?.body,
          }),
        );
        const current = latestTaskRef.current;
        if (agentSourceKey(current.inputParameters) !== requestedSourceKey) {
          return;
        }
        onChange(
          withAgentSnapshot(
            current as Partial<TaskDef> & {
              metadata?: Record<string, unknown>;
            },
            resolved,
          ),
        );
      } catch {
        const current = latestTaskRef.current;
        if (agentSourceKey(current.inputParameters) !== requestedSourceKey) {
          return;
        }
        onChange(
          withAgentSnapshot(
            current as Partial<TaskDef> & {
              metadata?: Record<string, unknown>;
            },
            createUnresolvedAgentSnapshot(input),
          ),
        );
        setResolutionWarning(
          input.agentType === "conductor"
            ? "The registered agent details could not be loaded. The agent remains configured and you can retry."
            : "The Agent Card could not be resolved. You can still save this task and retry.",
        );
      } finally {
        if (
          agentSourceKey(latestTaskRef.current.inputParameters) ===
          requestedSourceKey
        ) {
          setIsResolving(false);
        }
      }
    },
    [authHeaders, fetchContext, onChange],
  );

  useEffect(() => {
    if (
      !isConductor ||
      !agentName ||
      isDynamicAgentIdentity(agentName) ||
      lastAutomaticResolutionRef.current === sourceKey ||
      (isAgentSnapshotCurrent(snapshot, taskInput) && snapshot?.resolved)
    ) {
      return;
    }
    lastAutomaticResolutionRef.current = sourceKey;
    void resolveSnapshot(taskInput);
  }, [agentName, isConductor, resolveSnapshot, snapshot, sourceKey, taskInput]);

  const headers: Record<string, string> =
    (get("inputParameters.headers") as Record<string, string>) || {};

  const rawMessage = get("inputParameters.message");
  const messageJson =
    rawMessage == null
      ? ""
      : typeof rawMessage === "string"
        ? rawMessage
        : JSON.stringify(rawMessage, null, 2);

  const { data: agentDefinitions } = useFetch<AgentSummary[]>("/agent/list", {
    enabled: isConductor,
  });
  const agentNameOptions = useMemo(
    () =>
      Array.isArray(agentDefinitions)
        ? Array.from(new Set(agentDefinitions.map((a) => a.name))).sort()
        : [],
    [agentDefinitions],
  );

  return (
    <Box padding={1} width="100%">
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Agent"
      >
        <Grid container spacing={2} sx={{ width: "100%" }}>
          <Grid size={12}>
            <RadioButtonGroup
              name="agentType"
              value={agentType}
              onChange={(e) =>
                setSource("inputParameters.agentType", e.target.value)
              }
              items={AGENT_TYPES}
            />
          </Grid>
          {isConductor ? (
            <>
              <Grid size={{ xs: 12, md: 6 }}>
                <ConductorAutocompleteVariables
                  label="Agent name"
                  value={get("inputParameters.name") as string}
                  onChange={(v) => setSource("inputParameters.name", v)}
                  otherOptions={agentNameOptions}
                  placeholder="Select a registered agent"
                  openOnFocus
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Version (optional)"
                  value={get("inputParameters.version") as number}
                  coerceTo="integer"
                  onChange={(v) => setSource("inputParameters.version", v)}
                  placeholder="Latest"
                />
              </Grid>
              <Grid size={{ xs: 12, md: "auto" }} alignSelf="center">
                <Button
                  disabled={!agentName || agentName.includes("${")}
                  sx={{ fontSize: "12px" }}
                  onClick={() =>
                    window.open(
                      `${WORKFLOW_DEFINITION_URL.BASE}/${encodeURIComponent(agentName ?? "")}`,
                      "_blank",
                      "noopener,noreferrer",
                    )
                  }
                >
                  Open
                </Button>
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <ConductorAutocompleteVariables
                  label="Model override (optional)"
                  value={get("inputParameters.model") as string}
                  onChange={(v) => set("inputParameters.model", v)}
                  placeholder="e.g. openai/gpt-5"
                  inputProps={{
                    tooltip: {
                      title: "Model override",
                      content:
                        "Overrides this agent's model for this run only, in \"provider/model\" form (e.g. openai/gpt-5, anthropic/claude-opus-4-6). Leave blank to use the agent's own configured model.",
                    },
                  }}
                />
              </Grid>
              <Grid size={12}>
                <ConductorInput
                  label="Prompt"
                  name="prompt"
                  value={(get("inputParameters.prompt") as string) || ""}
                  onTextInputChange={(v) => set("inputParameters.prompt", v)}
                  multiline
                  rows={6}
                  fullWidth
                  required
                  error={promptMissing}
                  helperText={
                    promptMissing
                      ? "Required. This runtime reads the message from the prompt and nowhere else, so the workflow cannot be saved without one."
                      : undefined
                  }
                  placeholder="Message to send to the agent"
                />
              </Grid>
            </>
          ) : isProvider ? (
            <>
              {(PROVIDER_FIELDS[runtime] ?? [])
                .filter((field) => !field.tokenOnly || mintsToken)
                .map((field) => (
                  <Grid key={field.key} size={{ xs: 12, md: field.width ?? 6 }}>
                    <ConductorAutocompleteVariables
                      label={field.label}
                      value={
                        get(`inputParameters.rawConfig.${field.key}`) as string
                      }
                      onChange={(v) =>
                        setSource(`inputParameters.rawConfig.${field.key}`, v)
                      }
                      placeholder={field.placeholder}
                    />
                    {field.hint && (
                      <Typography
                        variant="caption"
                        color="text.secondary"
                        display="block"
                      >
                        {field.hint}
                      </Typography>
                    )}
                  </Grid>
                ))}
              <Grid size={12}>
                <AgentCredentialsSection
                  runtime={runtime}
                  credentials={
                    get("inputParameters.credentials") as
                      | Record<string, unknown>
                      | undefined
                  }
                  onCredentialsChange={(next) =>
                    set("inputParameters.credentials", next)
                  }
                  useCallerIdentity={!!get("inputParameters.useCallerIdentity")}
                  onUseCallerIdentityChange={(value) =>
                    set("inputParameters.useCallerIdentity", value)
                  }
                />
              </Grid>
              <Grid size={12}>
                <ConductorInput
                  label="Prompt"
                  name="prompt"
                  value={(get("inputParameters.prompt") as string) || ""}
                  onTextInputChange={(v) => set("inputParameters.prompt", v)}
                  multiline
                  rows={6}
                  fullWidth
                  required
                  error={promptMissing}
                  helperText={
                    promptMissing
                      ? "Required. This runtime reads the message from the prompt and nowhere else, so the workflow cannot be saved without one."
                      : undefined
                  }
                  placeholder="Message to send to the agent"
                />
              </Grid>
            </>
          ) : (
            <>
              <Grid size={12}>
                <ConductorAutocompleteVariables
                  label="Agent URL"
                  value={get("inputParameters.agentUrl") as string}
                  onChange={(v) => setSource("inputParameters.agentUrl", v)}
                  onBlur={() => void resolveSnapshot(taskInput)}
                />
              </Grid>
              <Grid size={12}>
                <ConductorInput
                  label="Message text"
                  name="text"
                  value={(get("inputParameters.text") as string) || ""}
                  onTextInputChange={(v) => set("inputParameters.text", v)}
                  multiline
                  rows={6}
                  fullWidth
                  placeholder="Message to send to the remote agent"
                />
              </Grid>
            </>
          )}
          {!isProvider && (
            <Grid size={12}>
              <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                <Button
                  variant="outlined"
                  size="small"
                  disabled={isResolving || !agentSourceIdentity(taskInput)}
                  onClick={() => void resolveSnapshot(taskInput)}
                >
                  Refresh agent details
                </Button>
                {isResolving && <CircularProgress size={18} />}
                {!isResolving && snapshot && (
                  <Typography variant="caption" color="text.secondary">
                    {isConductor
                      ? snapshot.resolved
                        ? "Details loaded"
                        : "Details unavailable"
                      : snapshot.resolved
                        ? "Resolved"
                        : "Unresolved"}
                  </Typography>
                )}
              </Box>
            </Grid>
          )}
          {resolutionWarning && (
            <Grid size={12}>
              <Alert severity="warning">{resolutionWarning}</Alert>
            </Grid>
          )}
        </Grid>
      </TaskFormSection>

      {snapshot && (
        <TaskFormSection title="Agent Card">
          <AgentSnapshotDetails snapshot={snapshot} />
        </TaskFormSection>
      )}

      {isProvider && (
        <TaskFormSection title="Tools">
          <Grid container spacing={2} sx={{ width: "100%" }}>
            <Grid size={12}>
              <FormControlLabel
                control={
                  <Switch
                    // Unset means on, so the switch has to show on. Turning it off writes false
                    // explicitly rather than clearing the key, which would read as on again.
                    checked={get("inputParameters.autoRunTools") !== false}
                    onChange={(e) =>
                      set("inputParameters.autoRunTools", e.target.checked)
                    }
                  />
                }
                label="Run the agent's tools as tasks in this workflow"
              />
              <Typography
                variant="caption"
                color="text.secondary"
                display="block"
              >
                The agent stays in progress while each tool it asks for is
                scheduled as a task of the same name, so a worker registered for
                that tool picks it up. Leave off to have the task complete and
                hand the tool request back to the workflow.
              </Typography>
            </Grid>
            {get("inputParameters.autoRunTools") !== false && (
              <Grid size={12}>
                <ConductorInput
                  label="Tool to task name overrides (JSON, optional)"
                  name="toolTaskNames"
                  value={jsonOrEmpty(get("inputParameters.toolTaskNames"))}
                  onTextInputChange={(v) =>
                    set("inputParameters.toolTaskNames", parseJsonOrRaw(v))
                  }
                  multiline
                  rows={3}
                  fullWidth
                  placeholder={'{ "get_revenue": "finance_revenue_lookup" }'}
                />
              </Grid>
            )}
          </Grid>
        </TaskFormSection>
      )}

      {isConductor && (
        <TaskFormSection title="Context">
          <ConductorFlatMapFormBase
            keyColumnLabel="Key"
            valueColumnLabel="Value"
            addItemLabel="Add context value"
            value={_path("inputParameters.context", task)}
            onChange={(value) =>
              onChange(updateField("inputParameters.context", value, task))
            }
          />
        </TaskFormSection>
      )}

      {isA2A && (
        <TaskFormSection title="Execution mode">
          <Box display="flex" flexDirection="column" mb={3}>
            <FormControlLabel
              control={
                <Switch
                  checked={!!get("inputParameters.streaming")}
                  onChange={(e) =>
                    set("inputParameters.streaming", e.target.checked)
                  }
                />
              }
              label="Streaming (SSE)"
            />
            <FormControlLabel
              control={
                <Switch
                  checked={!!get("inputParameters.pushNotification")}
                  onChange={(e) =>
                    set("inputParameters.pushNotification", e.target.checked)
                  }
                />
              }
              label="Push notification (webhook callback)"
            />
          </Box>
          <Grid container spacing={3} sx={{ width: "100%" }}>
            <Grid size={{ xs: 12, md: 6 }}>
              <ConductorAutocompleteVariables
                label="Push backstop poll (seconds)"
                value={get("inputParameters.pushBackstopPollSeconds") as number}
                coerceTo="integer"
                onChange={(v) =>
                  set("inputParameters.pushBackstopPollSeconds", v)
                }
              />
            </Grid>
          </Grid>
        </TaskFormSection>
      )}

      <TaskFormSection title="Polling and limits">
        <Grid container spacing={2} sx={{ width: "100%" }}>
          <Grid size={{ xs: 12, md: 6 }}>
            <ConductorAutocompleteVariables
              label="Poll interval (seconds)"
              value={get("inputParameters.pollIntervalSeconds") as number}
              coerceTo="integer"
              onChange={(v) => set("inputParameters.pollIntervalSeconds", v)}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <ConductorAutocompleteVariables
              label="Max duration (seconds)"
              value={get("inputParameters.maxDurationSeconds") as number}
              coerceTo="integer"
              onChange={(v) => set("inputParameters.maxDurationSeconds", v)}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <ConductorAutocompleteVariables
              label="Max poll failures"
              value={get("inputParameters.maxPollFailures") as number}
              coerceTo="integer"
              onChange={(v) => set("inputParameters.maxPollFailures", v)}
            />
          </Grid>
          {isA2A && (
            <Grid size={{ xs: 12, md: 6 }}>
              <ConductorAutocompleteVariables
                label="History length"
                value={get("inputParameters.historyLength") as number}
                coerceTo="integer"
                onChange={(v) => set("inputParameters.historyLength", v)}
              />
            </Grid>
          )}
        </Grid>
      </TaskFormSection>

      {isA2A && (
        <TaskFormSection title="Headers">
          <Grid container spacing={2} sx={{ width: "100%" }}>
            <Grid size={12}>
              <ConductorAdditionalHeadersBase
                headers={headers}
                onChangeHeaders={(h) => set("inputParameters.headers", h)}
              />
            </Grid>
          </Grid>
        </TaskFormSection>
      )}

      {isA2A && (
        <TaskFormSection title="Advanced message (optional)">
          <Grid container spacing={2} sx={{ width: "100%" }}>
            <Grid size={12}>
              <Typography variant="body2" color="text.secondary" mb={1}>
                Use <strong>Message text</strong> above for the common case.
                These override it for full control over the A2A message payload.
              </Typography>
            </Grid>
            <Grid size={12}>
              <ConductorCodeBlockInput
                label="Message (JSON)"
                language="json"
                minHeight={140}
                autoformat
                value={messageJson}
                onChange={(v) => set("inputParameters.message", v || undefined)}
              />
            </Grid>
            <Grid size={12}>
              <ConductorAutocompleteVariables
                label="Parts (variable reference)"
                value={get("inputParameters.parts") as string}
                onChange={(v) => set("inputParameters.parts", v || undefined)}
                placeholder="${workflow.input.parts}"
              />
            </Grid>
          </Grid>
        </TaskFormSection>
      )}

      {isA2A && (
        <TaskFormSection title="Advanced">
          <Grid container spacing={2} sx={{ width: "100%" }}>
            <Grid size={{ xs: 12, md: 6 }}>
              <ConductorAutocompleteVariables
                label="Context ID"
                value={get("inputParameters.contextId") as string}
                onChange={(v) => set("inputParameters.contextId", v)}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <ConductorAutocompleteVariables
                label="Task ID"
                value={get("inputParameters.taskId") as string}
                onChange={(v) => set("inputParameters.taskId", v)}
              />
            </Grid>
            <Grid size={12}>
              <ConductorAutocompleteVariables
                label="Metadata"
                value={get("inputParameters.metadata") as string}
                onChange={(v) => set("inputParameters.metadata", v)}
              />
            </Grid>
          </Grid>
        </TaskFormSection>
      )}

      <TaskFormSection>
        <Box display="flex" flexDirection="column" gap={3}>
          <ConductorCacheOutput onChange={onChange} taskJson={task} />
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
