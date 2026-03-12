import { Box, Grid } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import { TaskType } from "types";
import { MaybeVariable } from "../MaybeVariable";
import { Optional } from "../OptionalFieldForm";
import TaskFormSection from "../TaskFormSection";
import { useTaskForm } from "../hooks/useTaskForm";
import { TaskFormProps } from "../types";
import { useGetSetHandler } from "../useGetSetHandler";

const secretPath = "inputParameters._secrets";
const secretKeyPath = `${secretPath}.secretKey`;
const secretValuePath = `${secretPath}.secretValue`;

const UpdateSecretTaskForm = (props: TaskFormProps) => {
  const { task, onChange } = props;
  const [secretKey, setSecretKey] = useTaskForm(secretKeyPath, props);
  const [secretValue, setSecretValue] = useTaskForm(secretValuePath, props);
  const [secrets, handleSecrets] = useGetSetHandler(props, secretPath);

  return (
    <Box>
      <MaybeVariable
        value={secrets}
        onChange={handleSecrets}
        taskType={TaskType.UPDATE_SECRET}
        path={secretPath}
      >
        <TaskFormSection
          accordionAdditionalProps={{ defaultExpanded: true }}
          title="Secret Details"
        >
          <Grid container sx={{ width: "100%" }} spacing={3}>
            <Grid size={12}>
              <ConductorAutocompleteVariables
                label="Secret key"
                value={secretKey}
                onChange={setSecretKey}
              />
            </Grid>

            <Grid size={12}>
              <ConductorAutocompleteVariables
                label="Secret value"
                value={secretValue}
                onChange={setSecretValue}
              />
            </Grid>
          </Grid>
        </TaskFormSection>
      </MaybeVariable>

      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Box mt={3}>
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};

export default UpdateSecretTaskForm;
