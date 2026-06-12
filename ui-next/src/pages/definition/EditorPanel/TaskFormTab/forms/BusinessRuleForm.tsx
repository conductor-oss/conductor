import { Box, Grid } from "@mui/material";
import _get from "lodash/get";

import { ConductorArrayField } from "components/ui/inputs/ConductorArrayField";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapForm } from "components/FlatMapForm/ConductorFlatMapForm";
import { TaskType } from "types";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { updateField } from "utils/fieldHelpers";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import { useGetSetHandler } from "./useGetSetHandler";
import ConductorInput from "components/ui/inputs/ConductorInput";

const EXECUTION_STRATEGY_OPTIONS = ["FIRE_FIRST", "FIRE_ALL"];

const EXECUTION_STRATEGY_PATH = "inputParameters.executionStrategy";
const INPUT_COLUMNS_PATH = "inputParameters.inputColumns";
const OUTPUT_COLUMNS_PATH = "inputParameters.outputColumns";
const ruleFileLocationPath = "inputParameters.ruleFileLocation";
const cacheTimeoutMinutesPath = "inputParameters.cacheTimeoutMinutes";

export const BusinessRuleForm = (props: TaskFormProps) => {
  const { task, onChange } = props;
  const [executionStrategy, handlerExecutionStrategy] = useGetSetHandler(
    props,
    EXECUTION_STRATEGY_PATH,
  );
  const [inputColumns, handleInputColumns] = useGetSetHandler(
    props,
    INPUT_COLUMNS_PATH,
  );
  const [outputColumns, handleOutputColumns] = useGetSetHandler(
    props,
    OUTPUT_COLUMNS_PATH,
  );

  return (
    <Box width="100%">
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Grid container sx={{ width: "100%" }} spacing={2} mt={3}>
          <Grid
            size={{
              xs: 12,
              md: 6,
              sm: 12,
            }}
          >
            <ConductorAutocompleteVariables
              value={_get(task, ruleFileLocationPath)}
              label="Rule file location"
              onChange={(changes) =>
                onChange(updateField(ruleFileLocationPath, changes, task))
              }
            />
          </Grid>
          <Grid
            size={{
              xs: 12,
              md: 6,
              sm: 12,
            }}
          >
            <ConductorAutocompleteVariables
              onChange={handlerExecutionStrategy}
              value={executionStrategy}
              label="Execution strategy"
              otherOptions={EXECUTION_STRATEGY_OPTIONS}
            />
          </Grid>
        </Grid>

        <Box
          sx={{
            padding: "24px",
            backgroundColor: "#edf3fb",
            borderRadius: "8px",
            border: "1px solid #4B7BFB",
            color: "#194093",
            mt: 4,
          }}
        >
          <b>Note:</b> When you update the rules file, there is a default
          refresh interval of 60 mins. This can cause any updated rules to
          reflect only after a delay of up to 60 minutes. Override the refresh
          interval value below to adjust this delay to the required number of
          minutes.
        </Box>
      </TaskFormSection>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Grid container sx={{ width: "100%" }} spacing={2} mt={3}>
          <Grid
            size={{
              xs: 12,
              md: 6,
              sm: 12,
            }}
          >
            <ConductorInput
              label="Refresh Interval (Minutes)"
              type="number"
              value={_get(task, cacheTimeoutMinutesPath)}
              onTextInputChange={(changes) =>
                onChange(
                  updateField(cacheTimeoutMinutesPath, parseInt(changes), task),
                )
              }
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection
        title="Input columns"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorFlatMapForm
              showFieldTypes={true}
              keyColumnLabel="Key"
              valueColumnLabel="Value"
              addItemLabel="Add parameter"
              onChange={handleInputColumns}
              value={inputColumns}
              taskType={TaskType.BUSINESS_RULE}
              path={INPUT_COLUMNS_PATH}
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection title="Output columns">
        <Grid container sx={{ width: "100%" }} spacing={1}>
          <Grid size={12}>
            <ConductorArrayField
              value={outputColumns}
              onChange={handleOutputColumns}
              taskType={TaskType.BUSINESS_RULE}
              path={OUTPUT_COLUMNS_PATH}
              placeholder="Input value"
              enableAutocomplete
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection>
        <Box display="flex" flexDirection="column" gap={3}>
          <ConductorCacheOutput onChange={onChange} taskJson={task} />
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
