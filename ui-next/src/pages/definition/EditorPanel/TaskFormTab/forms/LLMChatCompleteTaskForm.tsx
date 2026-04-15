import { Box } from "@mui/material";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { fieldsToFieldsFieldsComponents } from "utils/fieldHelpers";
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

const promptFields = [UiIntegrationsFieldType.INSTRUCTIONS];

const messageFields = [UiIntegrationsFieldType.MESSAGES];

const fineTuningFields = [
  UiIntegrationsFieldType.TEMPERATURE,
  UiIntegrationsFieldType.TOP_P,
  UiIntegrationsFieldType.MAX_TOKENS,
  UiIntegrationsFieldType.STOP_WORDS,
];

const outputFields = [UiIntegrationsFieldType.JSON_OUTPUT];

const modelFieldComponents = fieldsToFieldsFieldsComponents(modelFields);
const promptFieldComponents = fieldsToFieldsFieldsComponents(promptFields);
const messageFieldComponents = fieldsToFieldsFieldsComponents(messageFields);
const fineTuningFieldComponents =
  fieldsToFieldsFieldsComponents(fineTuningFields);
const outputFieldComponents = fieldsToFieldsFieldsComponents(outputFields);

const allFieldComponents = [
  ...modelFieldComponents,
  ...promptFieldComponents,
  ...messageFieldComponents,
  ...fineTuningFieldComponents,
  ...outputFieldComponents,
];

export const LLMChatCompleteTaskForm = ({ task, onChange }: TaskFormProps) => {
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
            title="Provider and Model"
          >
            <LLMFormFields
              task={task}
              onChange={onChange}
              fieldFieldComponents={modelFieldComponents}
              actor={actor}
            />
          </TaskFormSection>
          <TaskFormSection title="Prompt and Variables">
            <LLMFormFields
              task={task}
              onChange={onChange}
              fieldFieldComponents={promptFieldComponents}
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
