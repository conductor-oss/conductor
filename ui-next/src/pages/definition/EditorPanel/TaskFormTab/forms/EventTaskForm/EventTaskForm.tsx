import { Box, Grid, Switch } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/v1/FlatMapForm/ConductorFlatMapForm";
import { path as _path } from "lodash/fp";
import { updateField } from "utils/fieldHelpers";
import { useEventNameSuggestions } from "utils/hooks";
import { Optional } from "../OptionalFieldForm";
import TaskFormSection from "../TaskFormSection";
import { TaskFormProps } from "../types";

const sinkPath = "sink";
const inputParametersPath = "inputParameters";
const asyncPath = "asyncComplete";

export const EventTaskForm = ({ task, onChange }: TaskFormProps) => {
  return (
    <Box>
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Destination"
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              label="Sink"
              otherOptions={useEventNameSuggestions()}
              value={_path(sinkPath, task)}
              onChange={(changes) =>
                onChange(updateField(sinkPath, changes, task))
              }
            />
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Input Parameters"
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorFlatMapFormBase
              showFieldTypes={true}
              keyColumnLabel="Key"
              valueColumnLabel="Value"
              addItemLabel="Add parameter"
              value={_path(inputParametersPath, task)}
              onChange={(changes) =>
                onChange(updateField(inputParametersPath, changes, task))
              }
            />
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection>
        <Box display="flex" flexDirection="column" gap={3}>
          <Box mt={3}>
            <Optional onChange={onChange} taskJson={task} />
          </Box>
          <Box>
            <Box sx={{ fontWeight: 600, color: "#767676", ml: -2.2 }}>
              <Switch
                color="primary"
                checked={task?.asyncComplete ?? false}
                onChange={(e) =>
                  onChange(updateField(asyncPath, e.target.checked, task))
                }
              />
              Async complete
            </Box>
            <Box style={{ opacity: 0.5 }}>
              When turned on, task completion occurs asynchronously, with the
              task remaining in progress while waiting for external APIs or
              events to complete the task.
            </Box>
          </Box>
        </Box>
      </TaskFormSection>
    </Box>
  );
};
