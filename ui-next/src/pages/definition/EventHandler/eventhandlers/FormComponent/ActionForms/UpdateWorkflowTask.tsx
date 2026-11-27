import { FormControlLabel, Grid } from "@mui/material";
import HelperText from "components/ui/inputs/HelperText";
import MuiCheckbox from "components/ui/MuiCheckbox";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import { Props } from "./common";
import { usePayloadMapProps } from "../payloadFields";
import { WorkflowIdInput } from "../PayloadFieldInput";

export const UpdateWorkflowForm = ({
  index,
  payload,
  handleChangeAction,
}: Props) => {
  const { update_workflow_variables } = payload;
  const payloadMapProps = usePayloadMapProps();

  return (
    <Grid container spacing={4} my={2} sx={{ width: "100%" }}>
      <Grid
        size={{
          xs: 12,
          md: 6,
        }}
      >
        <WorkflowIdInput
          fullWidth
          label="Workflow ID"
          placeholder={`\${workflowInstanceId}`}
          value={update_workflow_variables?.workflowId}
          onTextInputChange={(value) =>
            handleChangeAction(index, {
              ...payload,
              update_workflow_variables: {
                ...update_workflow_variables,
                workflowId: value,
              },
            })
          }
        />
      </Grid>
      <Grid size={12}>
        <FormControlLabel
          onChange={(event: any) =>
            handleChangeAction(index, {
              ...payload,
              update_workflow_variables: {
                ...update_workflow_variables,
                appendArray: event.target.checked,
              },
            })
          }
          control={
            <MuiCheckbox
              name="checkbox"
              checked={update_workflow_variables?.appendArray || false}
            />
          }
          label={"Append List Variables (instead of replacing)"}
          sx={{ color: "#767676", ">span": { fontWeight: 600 } }}
        />
        <HelperText>
          If this value is checked, all list (array) variables in the workflow
          will be treated as append instead of replace. This can be used to
          collect data from a series of events into a single workflow.
        </HelperText>
      </Grid>
      <Grid size={12}>
        <ConductorFlatMapFormBase
          onChange={(newValues) => {
            handleChangeAction(index, {
              ...payload,
              update_workflow_variables: {
                ...update_workflow_variables,
                variables: newValues,
              },
            });
          }}
          value={{ ...update_workflow_variables?.variables }}
          title="Variables"
          keyColumnLabel="Key"
          valueColumnLabel="Value"
          addItemLabel="Add parameter"
          showFieldTypes
          {...payloadMapProps}
          autoFocusField={false}
        />
      </Grid>
    </Grid>
  );
};
