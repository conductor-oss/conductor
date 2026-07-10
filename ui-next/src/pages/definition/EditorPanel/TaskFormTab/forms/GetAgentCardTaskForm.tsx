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

/** Config form for GET_AGENT_CARD — discovers a remote A2A agent's Agent Card. */
export const GetAgentCardTaskForm = ({ task, onChange }: TaskFormProps) => {
  const get = (p: string) => _path(p, task);
  const set = (p: string, value: any) => onChange(updateField(p, value, task));

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
              value={get("inputParameters.agentType") || "a2a"}
              onChange={(e) => set("inputParameters.agentType", e.target.value)}
              items={AGENT_TYPES}
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              label="Agent URL"
              value={get("inputParameters.agentUrl")}
              onChange={(v) => set("inputParameters.agentUrl", v)}
            />
          </Grid>
        </Grid>
      </TaskFormSection>

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

      <TaskFormSection>
        <Box display="flex" flexDirection="column" gap={3}>
          <ConductorCacheOutput onChange={onChange} taskJson={task} />
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
