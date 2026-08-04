import { Box } from "@mui/material";
import { LLMFormFields } from "pages/definition/EditorPanel/TaskFormTab/forms/LLMFormFields";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { fieldsToFieldsFieldsComponents } from "utils/fieldHelpers";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import LLMFormFieldsWrapper from "./LLMFormFields/LLMFormFieldsWrapper";

const promptFields = [UiIntegrationsFieldType.PROMPT_NAME];

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

const promptFieldComponents = fieldsToFieldsFieldsComponents(promptFields);
const modelFieldComponents = fieldsToFieldsFieldsComponents(modelFields);
const fineTuningFieldComponents =
  fieldsToFieldsFieldsComponents(fineTuningFields);

const allFieldComponents = [
  ...promptFieldComponents,
  ...modelFieldComponents,
  ...fineTuningFieldComponents,
];

export const LLMTextCompleteTaskForm = ({ task, onChange }: TaskFormProps) => {
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
            <LLMFormFields
              task={task}
              onChange={onChange}
              fieldFieldComponents={promptFieldComponents}
              actor={actor}
            />
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
