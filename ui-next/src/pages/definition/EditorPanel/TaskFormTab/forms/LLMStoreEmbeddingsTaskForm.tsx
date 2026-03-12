import { Box } from "@mui/material";
import { LLMFormFields } from "pages/definition/EditorPanel/TaskFormTab/forms/LLMFormFields";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { fieldsToFieldsFieldsComponents } from "utils/fieldHelpers";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import { ConductorFlatMapFormBase } from "components/v1/FlatMapForm/ConductorFlatMapForm";
import LLMFormFieldsWrapper from "./LLMFormFields/LLMFormFieldsWrapper";

const vectorDbFields = [
  UiIntegrationsFieldType.VECTOR_DB,
  UiIntegrationsFieldType.INDEX,
  UiIntegrationsFieldType.NAMESPACE,
  UiIntegrationsFieldType.ID,
];

const embeddingModelFields = [
  UiIntegrationsFieldType.EMBEDDING_MODEL_PROVIDER,
  UiIntegrationsFieldType.EMBEDDING_MODEL,
  UiIntegrationsFieldType.EMBEDDINGS,
];

const vectorDbFieldComponents = fieldsToFieldsFieldsComponents(vectorDbFields);
const embeddingModelFieldComponents =
  fieldsToFieldsFieldsComponents(embeddingModelFields);

const allFieldComponents = [
  ...vectorDbFieldComponents,
  ...embeddingModelFieldComponents,
];

export const LLMStoreEmbeddingsTaskForm = ({
  task,
  onChange,
}: TaskFormProps) => (
  <LLMFormFieldsWrapper
    task={task}
    onChange={onChange}
    allFieldComponents={allFieldComponents}
  >
    {(actor) => (
      <Box padding={1} width="100%">
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
        <TaskFormSection title="Metadata">
          <ConductorFlatMapFormBase
            keyColumnLabel="Key"
            valueColumnLabel="Value"
            addItemLabel="Add metadata"
            value={task?.inputParameters?.metadata}
            onChange={(newParams) =>
              onChange({
                ...task,
                inputParameters: {
                  ...(task?.inputParameters || {}),
                  metadata: newParams,
                },
              })
            }
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
