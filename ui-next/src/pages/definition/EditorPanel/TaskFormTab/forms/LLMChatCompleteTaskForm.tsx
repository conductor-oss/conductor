import { Box, Grid } from "@mui/material";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { path as _path } from "lodash/fp";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import {
  fieldsToFieldsFieldsComponents,
  updateField,
} from "utils/fieldHelpers";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { LLMFormFields } from "./LLMFormFields/LLMFormFields";
import LLMFormFieldsWrapper from "./LLMFormFields/LLMFormFieldsWrapper";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

const modelFields = [
  UiIntegrationsFieldType.LLM_PROVIDER,
  UiIntegrationsFieldType.MODEL,
];

const messageFields = [UiIntegrationsFieldType.MESSAGES];

const fineTuningFields = [
  UiIntegrationsFieldType.TEMPERATURE,
  UiIntegrationsFieldType.TOP_P,
  UiIntegrationsFieldType.MAX_TOKENS,
  UiIntegrationsFieldType.STOP_WORDS,
];

const outputFields = [UiIntegrationsFieldType.JSON_OUTPUT];

const modelFieldComponents = fieldsToFieldsFieldsComponents(modelFields);
const messageFieldComponents = fieldsToFieldsFieldsComponents(messageFields);
const fineTuningFieldComponents =
  fieldsToFieldsFieldsComponents(fineTuningFields);
const outputFieldComponents = fieldsToFieldsFieldsComponents(outputFields);

// INSTRUCTIONS is intentionally excluded from allFieldComponents — in OSS, the
// instructions/system-prompt is a plain textarea (below). Enterprise plugins
// override this section with a prompt-template picker that resolves promptName
// against the server's prompt library.
const allFieldComponents = [
  ...modelFieldComponents,
  ...messageFieldComponents,
  ...fineTuningFieldComponents,
  ...outputFieldComponents,
];

export const LLMChatCompleteTaskForm = ({ task, onChange }: TaskFormProps) => {
  const instructions = _path("inputParameters.instructions", task) || "";

  return (
    <LLMFormFieldsWrapper
      task={task}
      onChange={onChange}
      allFieldComponents={allFieldComponents}
    >
      {(actor) => (
        <Box padding={1} width="100%">
          {/* OSS: plain textarea for system instructions / prompt.
              Enterprise plugins replace this section with a prompt-template picker. */}
          <TaskFormSection
            accordionAdditionalProps={{ defaultExpanded: true }}
            title="Instructions"
          >
            <Grid container sx={{ width: "100%" }}>
              <Grid size={12}>
                <ConductorInput
                  label="Instructions"
                  name="instructions"
                  value={instructions}
                  onTextInputChange={(v) =>
                    onChange(
                      updateField("inputParameters.instructions", v, task),
                    )
                  }
                  multiline
                  rows={6}
                  fullWidth
                  placeholder="Enter system instructions or prompt for the model..."
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
          <TaskFormSection title="Structured Messages">
            <LLMFormFields
              task={task}
              onChange={onChange}
              fieldFieldComponents={messageFieldComponents}
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
          <TaskFormSection title="Output Format">
            <LLMFormFields
              task={task}
              onChange={onChange}
              fieldFieldComponents={outputFieldComponents}
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
