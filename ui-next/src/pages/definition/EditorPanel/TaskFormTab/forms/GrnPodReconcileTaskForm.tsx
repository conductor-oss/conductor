import { Box, Grid } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { path as _path } from "lodash/fp";
import { updateField } from "utils/fieldHelpers";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

const grnListPath = "inputParameters.grnList";
const podListPath = "inputParameters.podList";

export const GrnPodReconcileTaskForm = ({ task, onChange }: TaskFormProps) => {
  return (
    <Box width="100%">
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Reconciliation"
      >
        <Grid container spacing={3} pt={3}>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              fullWidth
              value={_path(grnListPath, task)}
              onChange={(changes) =>
                onChange(updateField(grnListPath, changes, task))
              }
              label="GRN List"
              inputProps={{
                tooltip: {
                  title: "GRN List",
                  content:
                    "List of extracted GRN records, usually from Gemini task output.",
                },
              }}
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              fullWidth
              value={_path(podListPath, task)}
              onChange={(changes) =>
                onChange(updateField(podListPath, changes, task))
              }
              label="POD List"
              inputProps={{
                tooltip: {
                  title: "POD List",
                  content:
                    "List of extracted POD records, usually from Gemini task output.",
                },
              }}
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
