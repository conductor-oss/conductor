import { Box, Grid } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import { path as _path } from "lodash/fp";
import { updateField } from "utils/fieldHelpers";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

const inputParametersPath = "inputParameters";
const dynamicForkTasksParamPath = "dynamicForkTasksParam";
const dynamicForkTasksInputParamNamePath = "dynamicForkTasksInputParamName";

export const DynamicForkOperatorForm = ({ task, onChange }: TaskFormProps) => (
  <Box width="100%">
    <TaskFormSection
      title="Input parameters"
      accordionAdditionalProps={{ defaultExpanded: true }}
    >
      <Grid container sx={{ width: "100%" }} spacing={3}>
        <Grid size={12}>
          <ConductorFlatMapFormBase
            keyColumnLabel="Key"
            valueColumnLabel="Value"
            addItemLabel="Add parameter"
            showFieldTypes={true}
            value={_path(inputParametersPath, task)}
            onChange={(value) =>
              onChange(updateField(inputParametersPath, value, task))
            }
          />
        </Grid>
      </Grid>
    </TaskFormSection>
    <TaskFormSection
      title="Parameter"
      accordionAdditionalProps={{ defaultExpanded: true }}
    >
      <Grid container spacing={3}>
        <Grid size={12}>
          <Box sx={{ fontSize: 12, opacity: 0.7, mb: 6 }}>
            Map parameters from above to tasks and inputs.
          </Box>
          <ConductorAutocompleteVariables
            fullWidth
            label="Tasks parameter"
            value={_path(dynamicForkTasksParamPath, task)}
            onChange={(value) =>
              onChange(updateField(dynamicForkTasksParamPath, value, task))
            }
          />
        </Grid>
        <Grid size={12}>
          <ConductorAutocompleteVariables
            fullWidth
            label="Input parameter name"
            value={_path(dynamicForkTasksInputParamNamePath, task)}
            onChange={(value) =>
              onChange(
                updateField(dynamicForkTasksInputParamNamePath, value, task),
              )
            }
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
