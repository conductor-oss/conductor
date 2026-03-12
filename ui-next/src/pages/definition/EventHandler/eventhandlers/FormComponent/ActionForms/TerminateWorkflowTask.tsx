import { Grid } from "@mui/material";
import IconButton from "components/MuiIconButton";
import MuiTypography from "components/MuiTypography";
import ConductorInput from "components/v1/ConductorInput";
import XCloseIcon from "components/v1/icons/XCloseIcon";
import { Props } from "./common";

export const TerminateWorkflowForm = ({
  onRemove,
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
    <Grid
      container
      spacing={4}
      my={2}
      sx={{ width: "100%", position: "relative" }}
    >
      <Grid size={12}>
        <MuiTypography fontWeight={800} fontSize={16}>
          Terminate Workflow
        </MuiTypography>
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
      <IconButton onClick={onRemove} sx={{ position: "absolute", right: 0 }}>
        <XCloseIcon size={26} />
      </IconButton>
    </Grid>
  );
};
