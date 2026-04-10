import { Grid, Stack } from "@mui/material";
import { ConductorAutoComplete } from "components/ui/inputs";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import { path as _path } from "lodash/fp";
import { updateField } from "utils/fieldHelpers";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import { useGetSetHandler } from "./useGetSetHandler";

const terminationStatusPath = "inputParameters.terminationStatus";
const terminationReasonPath = "inputParameters.terminationReason";
const workflowOutputPath = "inputParameters.workflowOutput";

export const TerminateOperatorForm = (props: TaskFormProps) => {
  const { task, onChange } = props;

  const [terminationReason, setTerminationReason] = useGetSetHandler(
    props,
    terminationReasonPath,
  );

  return (
    <Stack spacing={3}>
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Termination Options"
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid
            size={{
              xs: 12,
              sm: 12,
              md: 6,
            }}
          >
            <ConductorAutoComplete
              fullWidth
              label="Termination status"
              options={["COMPLETED", "FAILED", "TERMINATED"]}
              value={_path(terminationStatusPath, task)}
              onChange={(__, value) =>
                onChange(updateField(terminationStatusPath, value, task))
              }
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              label="Termination reason"
              value={terminationReason}
              onChange={setTerminationReason}
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection
        title="Workflow output"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <Grid container sx={{ width: "100%" }} spacing={2}>
          <Grid size={12}>
            <ConductorFlatMapFormBase
              showFieldTypes={true}
              keyColumnLabel="Key"
              valueColumnLabel="Value"
              addItemLabel="Add parameter"
              value={_path(workflowOutputPath, task)}
              onChange={(value) =>
                onChange(updateField(workflowOutputPath, value, task))
              }
            />
          </Grid>
          <Grid size={12}></Grid>
        </Grid>
      </TaskFormSection>
    </Stack>
  );
};
