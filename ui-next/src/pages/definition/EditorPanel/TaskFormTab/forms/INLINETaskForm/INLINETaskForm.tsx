import { Box, FormControlLabel, Grid } from "@mui/material";
import RadioButtonGroup from "components/ui/inputs/RadioButtonGroup";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import _path from "lodash/fp/path";
import { colors } from "theme/tokens/variables";
import { InlineTaskDef } from "types";
import { featureFlags, FEATURES } from "utils";
import { updateField } from "utils/fieldHelpers";
import { Optional } from "../OptionalFieldForm";
import TaskFormSection from "../TaskFormSection";
import { TaskFormProps } from "../types";
import InlineCodeBlock from "./InlineCodeBlock";

const hideJavascriptOption = featureFlags.isEnabled(
  FEATURES.HIDE_JAVASCRIPT_OPTION,
);

export const INLINETaskForm = ({ task, onChange }: TaskFormProps) => {
  const isJavascriptVisible =
    task?.inputParameters?.evaluatorType === "javascript";

  let options = [];
  if (hideJavascriptOption) {
    options = [
      {
        value: "graaljs",
        label: "ECMASCRIPT",
      },
    ];
  } else {
    options = [
      {
        value: "graaljs",
        label: "ECMASCRIPT",
      },
    ];
    if (isJavascriptVisible) {
      const javascriptOption = {
        value: "javascript",
        label: "Javascript(deprecated)",
        disabled: true,
      };
      options = [...options, javascriptOption];
    }
  }

  return (
    <Box width="100%">
      <TaskFormSection
        title="Script Parameters"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorFlatMapFormBase
              autoFocusField={false}
              showFieldTypes={true}
              keyColumnLabel="Key"
              valueColumnLabel="Value"
              addItemLabel="Add parameter"
              hiddenKeys={["evaluatorType", "expression"]}
              onChange={(data) =>
                onChange(updateField("inputParameters", data, task))
              }
              value={{ ...(task?.inputParameters || {}) }}
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Script"
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          {isJavascriptVisible && (
            <Grid
              sx={{ display: hideJavascriptOption ? "none" : "block" }}
              size={12}
            >
              <FormControlLabel
                labelPlacement="start"
                control={
                  <RadioButtonGroup
                    items={options}
                    name={"evaluatorType"}
                    value={_path("inputParameters.evaluatorType", task)}
                    onChange={(_event, value) => {
                      onChange(
                        updateField(
                          "inputParameters.evaluatorType",
                          value,
                          task,
                        ),
                      );
                    }}
                  />
                }
                label="Script:"
                sx={{
                  marginLeft: 0,
                  "& .MuiFormControlLabel-label": {
                    fontWeight: 600,
                    color: colors.gray07,
                  },
                }}
              />
            </Grid>
          )}

          <Grid sx={{ mt: "10px" }} size={12}>
            <InlineCodeBlock
              label="Code"
              language="javascript"
              minHeight={300}
              autoformat={false}
              languageLabel="ECMASCRIPT"
              autoSizeBox={true}
              task={task as Partial<InlineTaskDef>}
              onChange={onChange}
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

export default INLINETaskForm;
