import { Box, Grid } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import RadioButtonGroup from "components/ui/inputs/RadioButtonGroup";
import { path as _path } from "lodash/fp";
import { updateField } from "utils/fieldHelpers";
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
 * Config form for CANCEL_AGENT. `agentType: "a2a"` cancels a running task on a remote A2A agent
 * (Agent URL + Task ID); `"conductor"` terminates a Conductor agent execution — same shape as the
 * Terminate Workflow task (execution id + reason).
 */
export const CancelAgentTaskForm = ({ task, onChange }: TaskFormProps) => {
  const get = (p: string) => _path(p, task);
  const set = (p: string, value: any) => onChange(updateField(p, value, task));

  const agentType = (get("inputParameters.agentType") as string) || "a2a";
  const isConductor = agentType === "conductor";

  const headers: Record<string, string> =
    (get("inputParameters.headers") as Record<string, string>) || {};

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
              <Grid size={12}>
                <ConductorAutocompleteVariables
                  label="Execution ID"
                  value={get("inputParameters.executionId")}
                  onChange={(v) => set("inputParameters.executionId", v)}
                  placeholder="${agent_ref.output.executionId}"
                />
              </Grid>
              <Grid size={12}>
                <ConductorAutocompleteVariables
                  label="Reason"
                  value={get("inputParameters.reason")}
                  onChange={(v) => set("inputParameters.reason", v)}
                />
              </Grid>
            </>
          ) : (
            <>
              <Grid size={{ xs: 12, md: 8 }}>
                <ConductorAutocompleteVariables
                  label="Agent URL"
                  value={get("inputParameters.agentUrl")}
                  onChange={(v) => set("inputParameters.agentUrl", v)}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Task ID"
                  value={get("inputParameters.taskId")}
                  onChange={(v) => set("inputParameters.taskId", v)}
                />
              </Grid>
            </>
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

      <TaskFormSection>
        <Box display="flex" flexDirection="column" gap={3}>
          <ConductorCacheOutput onChange={onChange} taskJson={task} />
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
