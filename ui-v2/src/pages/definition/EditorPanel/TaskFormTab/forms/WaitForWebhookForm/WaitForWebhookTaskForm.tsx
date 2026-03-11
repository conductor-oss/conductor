import { Box, Grid } from "@mui/material";

import { ConductorFlatMapForm } from "components/v1/FlatMapForm/ConductorFlatMapForm";
import TaskFormSection from "pages/definition/EditorPanel/TaskFormTab/forms/TaskFormSection";
import { TaskFormProps } from "pages/definition/EditorPanel/TaskFormTab/forms/types";
import { TaskType } from "types";
import { Optional } from "../OptionalFieldForm";
import { useGetSetHandler } from "../useGetSetHandler";

const MATCH_PATH = "inputParameters.matches";
const WaitForWebhookTaskForm = (props: TaskFormProps) => {
  const { task, onChange } = props;
  const [match, handlerMatch] = useGetSetHandler(props, MATCH_PATH);
  return (
    <Box width="100%">
      <TaskFormSection
        title="Input matches:"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorFlatMapForm
              showFieldTypes={true}
              keyColumnLabel="Key"
              valueColumnLabel="Value"
              addItemLabel="Add"
              value={match}
              onChange={handlerMatch}
              taskType={TaskType.WAIT_FOR_WEBHOOK}
              path={MATCH_PATH}
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

export default WaitForWebhookTaskForm;
