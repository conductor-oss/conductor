import { Box, Grid, Typography } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { LLMFormFields } from "pages/definition/EditorPanel/TaskFormTab/forms/LLMFormFields";
import { path as _path } from "lodash/fp";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { fieldsToFieldsFieldsComponents, updateField } from "utils/fieldHelpers";
import { ConductorAdditionalHeadersBase } from "./HTTPTaskForm/ConductorAdditionalHeaders";
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
  const get = (p: string) => _path(p, task);
  const set = (p: string, value: any) => onChange(updateField(p, value, task));

  const metadata: Record<string, string> =
    (get("inputParameters.metadata") as Record<string, string>) || {};

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
            <Grid container spacing={3} sx={{ width: "100%" }} mt={0}>
              <Grid size={12}>
                <Typography variant="body2" color="text.secondary">
                  Provide a document <strong>URL</strong> above, or index
                  inline <strong>text</strong> directly below.
                </Typography>
              </Grid>
              <Grid size={12}>
                <ConductorInput
                  label="Text (inline)"
                  name="text"
                  value={(get("inputParameters.text") as string) || ""}
                  onTextInputChange={(v) => set("inputParameters.text", v)}
                  multiline
                  rows={4}
                  fullWidth
                  placeholder="Inline text to index (alternative to URL)"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <ConductorAutocompleteVariables
                  label="Document ID"
                  value={get("inputParameters.docId") as string}
                  onChange={(v) => set("inputParameters.docId", v)}
                />
              </Grid>
            </Grid>
          </TaskFormSection>
          <TaskFormSection title="Text Chunking">
            <LLMFormFields
              task={task}
              onChange={onChange}
              fieldFieldComponents={chunkingFieldComponents}
              actor={actor}
            />
          </TaskFormSection>
          <TaskFormSection title="Metadata">
            <Grid container spacing={2} sx={{ width: "100%" }}>
              <Grid size={12}>
                <ConductorAdditionalHeadersBase
                  headers={metadata}
                  onChangeHeaders={(m) => set("inputParameters.metadata", m)}
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
      )}
    </LLMFormFieldsWrapper>
  );
};
