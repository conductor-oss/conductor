import { Box, Grid } from "@mui/material";

import { ConductorCodeBlockInput } from "components/ui/inputs/ConductorCodeBlockInput";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { configurePromQl } from "utils/monacoUtils/CodeEditorUtils";
import { useTaskForm } from "../hooks/useTaskForm";
import { TaskFormProps } from "../types";

export const MetricsTypeForm = ({ task, onChange }: TaskFormProps) => {
  const [query, setQuery] = useTaskForm("inputParameters.metricsQuery", {
    task,
    onChange,
  });
  const [metricsStart, setMetricsStart] = useTaskForm(
    "inputParameters.metricsStart",
    {
      task,
      onChange,
    },
  );
  const [metricsEnd, setMetricsEnd] = useTaskForm(
    "inputParameters.metricsEnd",
    {
      task,
      onChange,
    },
  );
  const [metricsStep, setMetricsStep] = useTaskForm(
    "inputParameters.metricsStep",
    {
      task,
      onChange,
    },
  );
  return (
    <Grid container sx={{ width: "100%" }} spacing={3} mt={1}>
      <Grid size={12}>
        <ConductorCodeBlockInput
          language="promql"
          minHeight={100}
          autoformat={false}
          value={query}
          onChange={setQuery}
          beforeMount={configurePromQl}
        />
      </Grid>
      <Grid size={12}>
        <Grid container sx={{ width: "100%" }} alignItems={"center"} gap={2}>
          <Box>{"Start time from (Now - "} </Box>
          <Grid size={2}>
            <ConductorAutocompleteVariables
              label="Value"
              value={metricsStart}
              fullWidth
              onChange={setMetricsStart}
              growPopper
            />
          </Grid>
          <Box>{`mins) to (Now -`} </Box>
          <Grid size={2}>
            <ConductorAutocompleteVariables
              label="Value"
              value={metricsEnd}
              fullWidth
              onChange={setMetricsEnd}
              growPopper
            />
          </Grid>
          <Box>{"mins)"} </Box>
        </Grid>
      </Grid>
      <Grid size={12}>
        <ConductorAutocompleteVariables
          fullWidth
          label="Step"
          value={metricsStep}
          onChange={setMetricsStep}
        />
      </Grid>
    </Grid>
  );
};
