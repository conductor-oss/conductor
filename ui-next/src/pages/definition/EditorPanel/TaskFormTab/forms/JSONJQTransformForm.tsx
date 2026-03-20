import { Box, Grid } from "@mui/material";
import { ConductorCodeBlockInput } from "components/v1/ConductorCodeBlockInput";
import { ConductorFlatMapFormBase } from "components/v1/FlatMapForm/ConductorFlatMapForm";
import _path from "lodash/fp/path";
import { updateField } from "utils/fieldHelpers";
import { configureJQLanguage } from "utils/monacoUtils/CodeEditorUtils";
import JSONField from "./JSONField";
import { Optional } from "./OptionalFieldForm";

import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

const queryExpressionPath = "inputParameters.queryExpression";

export const JSONJQTransformForm = ({ task, onChange }: TaskFormProps) => {
  return (
    <Box>
      <TaskFormSection
        title="Script Parameters"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <JSONField
              path="inputParameters"
              onChange={onChange}
              taskJson={task}
            >
              <ConductorFlatMapFormBase
                showFieldTypes={true}
                keyColumnLabel="Key"
                valueColumnLabel="Value"
                addItemLabel="Add parameter"
                hiddenKeys={["queryExpression"]}
              />
            </JSONField>
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Code"
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorCodeBlockInput
              label="JQ expression"
              language="jq"
              beforeMount={configureJQLanguage}
              value={_path(queryExpressionPath, task)}
              onChange={(value) =>
                onChange(updateField(queryExpressionPath, value, task))
              }
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
