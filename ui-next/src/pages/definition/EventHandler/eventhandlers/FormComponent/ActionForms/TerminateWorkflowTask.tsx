import { Grid } from "@mui/material";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { Props } from "./common";

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
        <ConductorInput
          fullWidth
          label="Workflow ID"
          placeholder={`\${event.payload.workflow_id}`}
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
        <ConductorInput
          fullWidth
          label="Termination reason"
          placeholder="abcd"
          name="taskReference"
          value={terminate_workflow?.terminationReason}
          onTextInputChange={(value) =>
            handleChange("terminationReason", value)
          }
        />
      </Grid>
    </Grid>
  );
};
