import { Box } from "@mui/material";
import { LLMFormFields } from "pages/definition/EditorPanel/TaskFormTab/forms/LLMFormFields";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { fieldsToFieldsFieldsComponents } from "utils/fieldHelpers";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import LLMFormFieldsWrapper from "./LLMFormFields/LLMFormFieldsWrapper";

const modelFields = [
  UiIntegrationsFieldType.LLM_PROVIDER,
  UiIntegrationsFieldType.MODEL,
];

const embeddingFields = [
  UiIntegrationsFieldType.TEXT,
  UiIntegrationsFieldType.DIMENSIONS,
];

const modelFieldComponents = fieldsToFieldsFieldsComponents(modelFields);
const embeddingFieldComponents =
  fieldsToFieldsFieldsComponents(embeddingFields);

const allFieldComponents = [
  ...modelFieldComponents,
  ...embeddingFieldComponents,
];

export const LLMGenerateEmbeddingsTaskForm = ({
  task,
  onChange,
}: TaskFormProps) => {
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
          <TaskFormSection title="Embedding Configuration">
            <LLMFormFields
              task={task}
              onChange={onChange}
              fieldFieldComponents={embeddingFieldComponents}
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
