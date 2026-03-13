import { Box, FormControlLabel, Grid } from "@mui/material";
import MuiCheckbox from "components/MuiCheckbox";
import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import { updateField } from "utils/fieldHelpers";
import { Optional } from "../OptionalFieldForm";
import TaskFormSection from "../TaskFormSection";
import { TaskFormProps } from "../types";

const GET_WORKFLOW_ID_PATH = "inputParameters.id";
const GET_WORKFLOW_INCLUDE_PATH = "inputParameters.includeTasks";

export const GetWorkflowTaskForm = ({ task, onChange }: TaskFormProps) => {
  return (
    <Box width="100%">
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Grid container sx={{ width: "100%" }} spacing={2}>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              value={task?.inputParameters?.id ?? ""}
              label="Workflow ID"
              onChange={(value) =>
                onChange(updateField(GET_WORKFLOW_ID_PATH, value, task))
              }
            />
          </Grid>
          <Grid size={12}>
            <FormControlLabel
              control={
                <MuiCheckbox
                  checked={task?.inputParameters?.includeTasks ?? false}
                  onChange={(event) =>
                    onChange(
                      updateField(
                        GET_WORKFLOW_INCLUDE_PATH,
                        event.target.checked,
                        task,
                      ),
                    )
                  }
                />
              }
              label="Include tasks"
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Box mt={3}>
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
