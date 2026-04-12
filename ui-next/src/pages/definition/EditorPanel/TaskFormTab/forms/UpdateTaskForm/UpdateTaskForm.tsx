import { Box, FormControlLabel, Grid } from "@mui/material";

import MuiCheckbox from "components/ui/MuiCheckbox";
import {
  AnInputComponent,
  EventJson,
  UpdateTaskFormEvent,
} from "pages/definition/EditorPanel/TaskFormTab/forms/UpdateTaskForm/UpdateTaskFromEvent";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import { updateField } from "utils/fieldHelpers";
import TaskFormSection from "../TaskFormSection";
import { TaskFormProps } from "../types";
import { useUpdateTaskHandler } from "./common";
import { UpdateTaskStatus } from "types/UpdateTaskStatus";
import { Optional } from "../OptionalFieldForm";

export const UpdateTaskForm = ({ task, onChange }: TaskFormProps) => {
  const { handleTaskStatusChange, handleMergeOutputChange } =
    useUpdateTaskHandler({
      task,
      onChange,
    });
  return (
    <Box>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <UpdateTaskFormEvent
              value={task?.inputParameters as EventJson}
              onChange={(value) => {
                onChange({
                  ...task,
                  inputParameters: {
                    ...task.inputParameters,
                    ...{
                      workflowId: value?.workflowId,
                      taskId: value?.taskId,
                      taskRefName: value?.taskRefName,
                    },
                  },
                });
              }}
              inputComponent={
                ConductorAutocompleteVariables as AnInputComponent
              }
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              value={task?.inputParameters?.taskStatus}
              label="Task status"
              onChange={(value) => handleTaskStatusChange(value)}
              otherOptions={Object.values(UpdateTaskStatus)}
              error={!task?.inputParameters?.taskStatus}
            />
          </Grid>

          <Grid size={12}>
            <FormControlLabel
              control={
                <MuiCheckbox
                  checked={task?.inputParameters?.mergeOutput ?? false}
                  onChange={handleMergeOutputChange}
                />
              }
              label="Merge Output (append the output to existing output)"
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection
        title="Task output"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <ConductorFlatMapFormBase
          showFieldTypes
          keyColumnLabel="Key"
          valueColumnLabel="Value"
          addItemLabel="Add params"
          value={task?.inputParameters?.taskOutput}
          onChange={(value) =>
            onChange(updateField("inputParameters.taskOutput", value, task))
          }
        />
      </TaskFormSection>

      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Box mt={3}>
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
