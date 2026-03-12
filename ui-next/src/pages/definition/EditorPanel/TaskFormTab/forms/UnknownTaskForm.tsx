import TaskFormSection from "pages/definition/EditorPanel/TaskFormTab/forms/TaskFormSection";
import { Box, Grid } from "@mui/material";
import { TaskFormProps } from "pages/definition/EditorPanel/TaskFormTab/forms/types";
import { ConductorFlatMapFormBase } from "components/v1/FlatMapForm/ConductorFlatMapForm";

export const UnknownTaskForm = ({ task, onChange }: TaskFormProps) => {
  return (
    <Box width="100%">
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Input parameters"
      >
        <Grid container sx={{ width: "100%" }} spacing={2}>
          <Grid size={12}>
            <ConductorFlatMapFormBase
              showFieldTypes={true}
              keyColumnLabel="Key"
              valueColumnLabel="Value"
              addItemLabel="Add parameter"
              value={task?.inputParameters}
              onChange={(newParams) =>
                onChange({ ...task, inputParameters: newParams })
              }
            />
          </Grid>
        </Grid>
      </TaskFormSection>
    </Box>
  );
};
