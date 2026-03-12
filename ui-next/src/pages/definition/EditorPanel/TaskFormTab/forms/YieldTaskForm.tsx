import { useState } from "react";
import { Box, Grid } from "@mui/material";
import { path as _path } from "lodash/fp";

import { ConductorFlatMapFormBase } from "components/v1/FlatMapForm/ConductorFlatMapForm";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";

import { SnackbarMessage } from "components/SnackbarMessage";

import { SchemaForm } from "./SchemaForm";
import { TaskFormProps } from "./types";
import TaskFormSection from "./TaskFormSection";
import { updateField } from "utils/fieldHelpers";
import { Optional } from "./OptionalFieldForm";
import { useSchemaFormHandler } from "./hooks/useSchemaFormHandler";

const inputParametersPath = "inputParameters";

export const YieldTaskForm = ({ task, onChange }: TaskFormProps) => {
  const [showAlert, setShowAlert] = useState(false);
  const handleSchemaChange = useSchemaFormHandler({ task, onChange });

  return (
    <Box width="100%">
      {showAlert && (
        <SnackbarMessage
          message="Copied to Clipboard"
          severity="success"
          onDismiss={() => setShowAlert(false)}
          anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
        />
      )}
      <TaskFormSection
        title="Input parameters"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorFlatMapFormBase
              showFieldTypes
              keyColumnLabel="Key"
              valueColumnLabel="Value"
              addItemLabel="Add parameter"
              value={_path(inputParametersPath, task)}
              onChange={(value) =>
                onChange(updateField(inputParametersPath, value, task))
              }
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
      <SchemaForm value={task.taskDefinition} onChange={handleSchemaChange} />
    </Box>
  );
};
