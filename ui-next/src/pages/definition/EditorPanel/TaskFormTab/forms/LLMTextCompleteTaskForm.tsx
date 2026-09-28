import { Box, Grid } from "@mui/material";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { path as _path } from "lodash/fp";
import { LLMFormFields } from "pages/definition/EditorPanel/TaskFormTab/forms/LLMFormFields";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import {
  fieldsToFieldsFieldsComponents,
  updateField,
} from "utils/fieldHelpers";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import LLMFormFieldsWrapper from "./LLMFormFields/LLMFormFieldsWrapper";

const modelFields = [
  UiIntegrationsFieldType.LLM_PROVIDER,
  UiIntegrationsFieldType.MODEL,
];

const fineTuningFields = [
  UiIntegrationsFieldType.TEMPERATURE,
  UiIntegrationsFieldType.TOP_P,
  UiIntegrationsFieldType.MAX_TOKENS,
  UiIntegrationsFieldType.STOP_WORDS,
];

const modelFieldComponents = fieldsToFieldsFieldsComponents(modelFields);
const fineTuningFieldComponents =
  fieldsToFieldsFieldsComponents(fineTuningFields);

// prompt is a plain textarea (below), not the saved-prompt picker (PROMPT_NAME).
// Enterprise plugins override with the saved AI Prompt picker (see conductor-ui).
const allFieldComponents = [
  ...modelFieldComponents,
  ...fineTuningFieldComponents,
];

export const LLMTextCompleteTaskForm = ({ task, onChange }: TaskFormProps) => {
  const prompt = _path("inputParameters.prompt", task) || "";

  return (
    <LLMFormFieldsWrapper
      task={task}
      onChange={onChange}
      allFieldComponents={allFieldComponents}
    >
      {(actor) => (
        <Box padding={1} width="100%">
          <TaskFormSection
            accordionAdditionalProps={{ defaultExpanded: true }}
            title="Prompt"
          >
            <Grid container sx={{ width: "100%" }}>
              <Grid size={12}>
                <ConductorInput
                  label="Prompt"
                  name="prompt"
                  value={prompt}
                  onTextInputChange={(v) =>
                    onChange(updateField("inputParameters.prompt", v, task))
                  }
                  multiline
                  rows={6}
                  fullWidth
                  placeholder="Enter the text prompt to complete..."
                />
              </Grid>
            </Grid>
          </TaskFormSection>
          <TaskFormSection
            accordionAdditionalProps={{ defaultExpanded: true }}
            title="Provider and Model"
          >
            <LLMFormFields
              task={task}
              onChange={onChange}
              fieldFieldComponents={modelFieldComponents}
              actor={actor}
            />
          </TaskFormSection>
          <TaskFormSection title="Fine Tuning">
            <LLMFormFields
              task={task}
              onChange={onChange}
              fieldFieldComponents={fineTuningFieldComponents}
              actor={actor}
            />
          </TaskFormSection>
          <TaskFormSection>
            <Box display="flex" flexDirection="column" gap={3}>
              <ConductorCacheOutput onChange={onChange} taskJson={task} />
              <Optional onChange={onChange} taskJson={task} />
            </Box>
          </TaskFormSection>
        </Box>
      )}
    </LLMFormFieldsWrapper>
  );
};
