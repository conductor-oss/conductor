import { Box, Grid, Typography } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { path as _path } from "lodash/fp";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import {
  fieldsToFieldsFieldsComponents,
  updateField,
} from "utils/fieldHelpers";
import { ConductorAdditionalHeadersBase } from "./HTTPTaskForm/ConductorAdditionalHeaders";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { LLMFormFields } from "./LLMFormFields/LLMFormFields";
import LLMFormFieldsWrapper from "./LLMFormFields/LLMFormFieldsWrapper";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

const vectorDbFields = [
  UiIntegrationsFieldType.VECTOR_DB,
  UiIntegrationsFieldType.INDEX,
  UiIntegrationsFieldType.NAMESPACE,
];

const embeddingModelFields = [
  UiIntegrationsFieldType.EMBEDDING_MODEL_PROVIDER,
  UiIntegrationsFieldType.EMBEDDING_MODEL,
];

const searchFields = [
  UiIntegrationsFieldType.QUERY,
  UiIntegrationsFieldType.MAX_RESULTS,
  UiIntegrationsFieldType.DIMENSIONS,
];

const vectorDbFieldComponents = fieldsToFieldsFieldsComponents(vectorDbFields);
const embeddingModelFieldComponents =
  fieldsToFieldsFieldsComponents(embeddingModelFields);
const searchFieldComponents = fieldsToFieldsFieldsComponents(searchFields);

const allFieldComponents = [
  ...vectorDbFieldComponents,
  ...embeddingModelFieldComponents,
  ...searchFieldComponents,
];

/**
 * Config form for LLM_SEARCH_EMBEDDINGS — searches a vector database using pre-computed embeddings
 * (as opposed to LLM_SEARCH_INDEX, which generates embeddings from a query string).
 */
export const LLMSearchEmbeddingsTaskForm = ({
  task,
  onChange,
}: TaskFormProps) => {
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
        <Box padding={1} width="100%">
          <TaskFormSection
            accordionAdditionalProps={{ defaultExpanded: true }}
            title="Vector Database"
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

          <TaskFormSection
            accordionAdditionalProps={{ defaultExpanded: true }}
            title="Search"
          >
            <Grid container spacing={3} sx={{ width: "100%" }}>
              <Grid size={12}>
                <ConductorAutocompleteVariables
                  label="Embeddings"
                  value={get("inputParameters.embeddings") as string}
                  onChange={(v) => set("inputParameters.embeddings", v)}
                  placeholder="${generateEmbeddings.output.result}"
                />
              </Grid>
              <Grid size={12}>
                <Typography variant="body2" color="text.secondary" mb={1}>
                  When <strong>embeddings</strong> is set it is used directly;
                  otherwise the <strong>query</strong> below is embedded with
                  the selected embedding model.
                </Typography>
              </Grid>
            </Grid>
            <Box mt={3}>
              <LLMFormFields
                task={task}
                onChange={onChange}
                fieldFieldComponents={searchFieldComponents}
                actor={actor}
              />
            </Box>
          </TaskFormSection>

          <TaskFormSection title="Metadata filter">
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
