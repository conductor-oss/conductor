import { Box, FormControlLabel, Grid } from "@mui/material";
import MuiCheckbox from "components/ui/MuiCheckbox";
import { AutocompleteArrayField } from "components/FlatMapForm/ConductorAutocompleteArrayField";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { TaskType } from "types/common";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import { useGetSetHandler } from "./useGetSetHandler";

const workFlowId = "inputParameters.workflowId";
const terminationReasonPath = "inputParameters.terminationReason";
const triggerFailureWorkflowPath = "inputParameters.triggerFailureWorkflow";

export const TerminateWorkflowForm = (props: TaskFormProps) => {
  const { task, onChange } = props;
  const triggerHandleChange = () => {
    setTriggerFailureWorkflow(!triggerFailureWorkflow);
  };

  const [workFlowIds, handleWorkFlowIds] = useGetSetHandler(props, workFlowId);

  const [terminationReason, setTerminationReason] = useGetSetHandler(
    props,
    terminationReasonPath,
  );

  const [triggerFailureWorkflow, setTriggerFailureWorkflow] = useGetSetHandler(
    props,
    triggerFailureWorkflowPath,
  );

  return (
    <Box padding={1} width="100%">
      <TaskFormSection title="Workflow ids:">
        <Grid container sx={{ width: "100%" }} spacing={1}>
          <Grid size={12}>
            <AutocompleteArrayField
              label="Workflow id"
              value={workFlowIds}
              onChange={handleWorkFlowIds}
              taskType={TaskType.TERMINATE_WORKFLOW}
              path={workFlowId}
              hasAtLeastOne
              placeholder="someWorkflowID"
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Grid container sx={{ width: "100%" }} spacing={2} mt={4}>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              value={terminationReason}
              label="Termination reason:"
              onChange={setTerminationReason}
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <FormControlLabel
          onChange={triggerHandleChange}
          control={
            <MuiCheckbox
              name={"joinScript"}
              checked={triggerFailureWorkflow ?? false}
            />
          }
          label={"Trigger Failure Workflow"}
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
