import { Box, FormControlLabel, Grid, Switch, Typography } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import Button from "components/ui/buttons/MuiButton";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import RadioButtonGroup from "components/ui/inputs/RadioButtonGroup";
import { path as _path } from "lodash/fp";
import { useMemo } from "react";
import { AgentSummary } from "pages/agent/types";
import { WORKFLOW_DEFINITION_URL } from "utils/constants/route";
import { updateField } from "utils/fieldHelpers";
import { useFetch } from "utils/query";
import { ConductorAdditionalHeadersBase } from "./HTTPTaskForm/ConductorAdditionalHeaders";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

const AGENT_TYPES = [
  { value: "a2a", label: "A2A" },
  { value: "conductor", label: "Conductor" },
];

/**
 * Config form for the AGENT task. Two runtimes, one task type, disjoint input shapes:
 * `agentType: "a2a"` calls a remote Agent2Agent endpoint (poll / streaming / push modes); `"conductor"`
 * runs a registered agent on the embedded agentspan runtime — its input mirrors `POST /api/agent/start`
 * (`AgentStartRequest`), not the A2A message shape.
 */
export const AgentTaskForm = ({ task, onChange }: TaskFormProps) => {
  const get = (p: string) => _path(p, task);
  const set = (p: string, value: any) => onChange(updateField(p, value, task));

  const agentType = (get("inputParameters.agentType") as string) || "a2a";
  const isConductor = agentType === "conductor";
  const agentName = get("inputParameters.name") as string | undefined;

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
              onChange={(e) => set("inputParameters.agentType", e.target.value)}
              items={AGENT_TYPES}
            />
          </Grid>
          {isConductor ? (
            <>
              <Grid size={{ xs: 12, md: 9 }}>
                <ConductorAutocompleteVariables
                  label="Agent name"
                  value={get("inputParameters.name") as string}
                  onChange={(v) => set("inputParameters.name", v)}
                  otherOptions={agentNameOptions}
                  placeholder="Select a registered agent"
                  openOnFocus
                />
              </Grid>
              <Grid
                size={{ xs: 12, md: 3 }}
                sx={{ display: "flex", alignItems: "center" }}
              >
                <Button
                  fullWidth
                  disabled={!agentName || agentName.includes("${")}
                  onClick={() =>
                    window.open(
                      `${WORKFLOW_DEFINITION_URL.BASE}/${encodeURIComponent(agentName ?? "")}`,
                      "_blank",
                      "noopener,noreferrer",
                    )
                  }
                >
                  Open agent definition
                </Button>
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <ConductorAutocompleteVariables
                  label="Version (optional)"
                  value={get("inputParameters.version") as number}
                  coerceTo="integer"
                  onChange={(v) => set("inputParameters.version", v)}
                  placeholder="Latest"
                />
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
                  onChange={(v) => set("inputParameters.agentUrl", v)}
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
        </Grid>
      </TaskFormSection>

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

      {!isConductor && (
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
          {!isConductor && (
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

      {!isConductor && (
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

      {!isConductor && (
        <TaskFormSection title="Advanced message (optional)">
          <Grid container spacing={2} sx={{ width: "100%" }}>
            <Grid size={12}>
              <Typography variant="body2" color="text.secondary" mb={1}>
                Use <strong>Message text</strong> above for the common case.
                These override it for full control over the A2A message
                payload.
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

      {!isConductor && (
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
