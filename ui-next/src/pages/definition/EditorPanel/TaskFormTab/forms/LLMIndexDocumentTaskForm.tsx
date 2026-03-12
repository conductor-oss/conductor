import { Box } from "@mui/material";
import { LLMFormFields } from "pages/definition/EditorPanel/TaskFormTab/forms/LLMFormFields";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { fieldsToFieldsFieldsComponents } from "utils/fieldHelpers";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import LLMFormFieldsWrapper from "./LLMFormFields/LLMFormFieldsWrapper";

const vectorDbFields = [
  UiIntegrationsFieldType.VECTOR_DB,
  UiIntegrationsFieldType.INDEX,
  UiIntegrationsFieldType.NAMESPACE,
];

const embeddingModelFields = [
  UiIntegrationsFieldType.EMBEDDING_MODEL_PROVIDER,
  UiIntegrationsFieldType.EMBEDDING_MODEL,
  UiIntegrationsFieldType.DIMENSIONS,
];

const documentFields = [
  UiIntegrationsFieldType.URL,
  UiIntegrationsFieldType.MEDIA_TYPE,
];

const chunkingFields = [
  UiIntegrationsFieldType.CHUNK_SIZE,
  UiIntegrationsFieldType.CHUNK_OVERLAP,
];

const vectorDbFieldComponents = fieldsToFieldsFieldsComponents(vectorDbFields);
const embeddingModelFieldComponents =
  fieldsToFieldsFieldsComponents(embeddingModelFields);
const documentFieldComponents = fieldsToFieldsFieldsComponents(documentFields);
const chunkingFieldComponents = fieldsToFieldsFieldsComponents(chunkingFields);

const allFieldComponents = [
  ...vectorDbFieldComponents,
  ...embeddingModelFieldComponents,
  ...documentFieldComponents,
  ...chunkingFieldComponents,
];

export const LLMIndexDocumentTaskForm = ({ task, onChange }: TaskFormProps) => {
  return (
    <LLMFormFieldsWrapper
      task={task}
      onChange={onChange}
      allFieldComponents={allFieldComponents}
    >
      {(actor) => (
        <Box padding={1} width="100%" key={task.taskReferenceName}>
          <TaskFormSection
            accordionAdditionalProps={{ defaultExpanded: true }}
            title="Vector Database Configuration"
          >
            <LLMFormFields
              task={task}
              onChange={onChange}
              fieldFieldComponents={vectorDbFieldComponents}
              actor={actor}
            />
          </TaskFormSection>
          <TaskFormSection title="Embedding Model">
            <LLMFormFields
              task={task}
              onChange={onChange}
              fieldFieldComponents={embeddingModelFieldComponents}
              actor={actor}
            />
          </TaskFormSection>
          <TaskFormSection title="Document Source">
            <LLMFormFields
              task={task}
              onChange={onChange}
              fieldFieldComponents={documentFieldComponents}
              actor={actor}
            />
          </TaskFormSection>
          <TaskFormSection title="Text Chunking">
            <LLMFormFields
              task={task}
              onChange={onChange}
              fieldFieldComponents={chunkingFieldComponents}
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
