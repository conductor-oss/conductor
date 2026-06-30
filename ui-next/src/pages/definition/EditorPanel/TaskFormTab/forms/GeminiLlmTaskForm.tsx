import { Box, FormControlLabel, Grid, Switch } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { path as _path } from "lodash/fp";
import { updateField } from "utils/fieldHelpers";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

const connectionIdPath = "inputParameters.connectionId";
const modelPath = "inputParameters.model";
const promptnamePath = "inputParameters.promptname";
const legacyPromptNamePath = "inputParameters.promptName";
const promptPath = "inputParameters.prompt";
const documentsPath = "inputParameters.documents";
const filesPath = "inputParameters.files";
const jsonOutputPath = "inputParameters.jsonOutput";

export const GeminiLlmTaskForm = ({ task, onChange }: TaskFormProps) => {
  const jsonOutput = Boolean(_path(jsonOutputPath, task));

  return (
    <Box width="100%">
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Gemini"
      >
        <Grid container spacing={3} pt={3}>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              fullWidth
              value={
                _path(promptnamePath, task) ?? _path(legacyPromptNamePath, task)
              }
              onChange={(changes) =>
                onChange(updateField(promptnamePath, changes, task))
              }
              label="Prompt Name"
              placeholder="select j2"
              inputProps={{
                tooltip: {
                  title: "Prompt Name",
                  content:
                    "Name of the .j2 file under prompts to execute when Prompt is empty.",
                },
              }}
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              fullWidth
              value={_path(promptPath, task)}
              onChange={(changes) =>
                onChange(updateField(promptPath, changes, task))
              }
              label="Prompt"
              placeholder="paste your prompt here"
              inputProps={{
                tooltip: {
                  title: "Prompt",
                  content:
                    "Direct prompt text to execute instead of loading a prompt template.",
                },
              }}
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              fullWidth
              value={_path(connectionIdPath, task)}
              onChange={(changes) =>
                onChange(updateField(connectionIdPath, changes, task))
              }
              label="Connection ID"
              inputProps={{
                tooltip: {
                  title: "Connection ID",
                  content:
                    "Gemini connection used to resolve the stored API key for this task.",
                },
              }}
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              fullWidth
              value={_path(modelPath, task)}
              onChange={(changes) =>
                onChange(updateField(modelPath, changes, task))
              }
              label="Model"
            />
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Input"
      >
        <Grid container spacing={3} pt={3}>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              fullWidth
              value={_path(documentsPath, task)}
              onChange={(changes) =>
                onChange(updateField(documentsPath, changes, task))
              }
              label="Documents"
              inputProps={{
                tooltip: {
                  title: "Documents",
                  content:
                    "List of documents to process. The task runs Gemini once per document and returns list outputs.",
                },
              }}
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              fullWidth
              value={_path(filesPath, task)}
              onChange={(changes) =>
                onChange(updateField(filesPath, changes, task))
              }
              label="Files"
              inputProps={{
                tooltip: {
                  title: "Files",
                  content:
                    "Optional file payload or workflow expression when not using documents.",
                },
              }}
            />
          </Grid>
          <Grid size={12}>
            <FormControlLabel
              control={
                <Switch
                  checked={jsonOutput}
                  color="primary"
                  onChange={(event) =>
                    onChange(
                      updateField(jsonOutputPath, event.target.checked, task),
                    )
                  }
                />
              }
              label="JSON Output"
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
