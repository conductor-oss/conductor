import { Grid } from "@mui/material";
import { Props } from "./common";
import PayloadFieldInput, { WorkflowIdInput } from "../PayloadFieldInput";

export const TerminateWorkflowForm = ({
  index,
  payload,
  handleChangeAction,
}: Props) => {
  const { terminate_workflow } = payload;

  const handleChange = (field: string, value: string) => {
    handleChangeAction(index, {
      ...payload,
      terminate_workflow: {
        ...terminate_workflow,
        [field]: value,
      },
    });
  };

  return (
    <Grid container spacing={4} my={2} sx={{ width: "100%" }}>
      <Grid
        size={{
          xs: 12,
          sm: 12,
          md: 6,
        }}
      >
        <WorkflowIdInput
          fullWidth
          label="Workflow ID"
          placeholder={`\${workflowInstanceId}`}
          value={terminate_workflow?.workflowId}
          onTextInputChange={(value) => handleChange("workflowId", value)}
        />
      </Grid>
      <Grid
        size={{
          xs: 12,
          sm: 12,
          md: 6,
        }}
      >
        <PayloadFieldInput
          fullWidth
          label="Termination reason"
          placeholder="e.g. Order cancelled"
          value={terminate_workflow?.terminationReason}
          onTextInputChange={(value) =>
            handleChange("terminationReason", value)
          }
        />
      </Grid>
    </Grid>
  );
};
