import { Box, Grid } from "@mui/material";
import { path as _path } from "lodash/fp";

import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import { updateField } from "utils/fieldHelpers";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

const inputParametersPath = "inputParameters";

export const DynamicForkForm = ({ task, onChange }: TaskFormProps) => (
  <Box width="100%">
    <TaskFormSection
      title="Task input params"
      accordionAdditionalProps={{ defaultExpanded: true }}
    >
      <Grid container sx={{ width: "100%" }} spacing={3}>
        <Grid size={12}>
          <ConductorFlatMapFormBase
            keyColumnLabel="Key"
            valueColumnLabel="Value"
            addItemLabel="Add parameter"
            value={_path(inputParametersPath, task)}
            onChange={(value) =>
              onChange(updateField(inputParametersPath, value, task))
            }
          />
        </Grid>

        <Grid size={12}>
          <ConductorAutocompleteVariables
            label="Dynamic Task to be Executed"
            value={task.dynamicTaskNameParam}
            onChange={(changes) =>
              onChange(updateField("dynamicTaskNameParam", changes, task))
            }
            inputProps={{
              tooltip: {
                title: "Dynamic Task to be Executed",
                content:
                  "Indicates the name of the task, or the variable, to be called during workflow execution.",
              },
            }}
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
