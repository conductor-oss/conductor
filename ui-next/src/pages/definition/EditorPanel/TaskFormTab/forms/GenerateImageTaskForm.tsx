import { Box, Grid } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
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
const modelFieldComponents = fieldsToFieldsFieldsComponents(modelFields);

/** Config form for GENERATE_IMAGE — generates images from a text prompt using an LLM provider. */
export const GenerateImageTaskForm = ({ task, onChange }: TaskFormProps) => {
  const get = (p: string) => _path(p, task);
  const set = (p: string, value: any) => onChange(updateField(p, value, task));

  return (
    <LLMFormFieldsWrapper
      task={task}
      onChange={onChange}
      allFieldComponents={modelFieldComponents}
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

          <TaskFormSection
            accordionAdditionalProps={{ defaultExpanded: true }}
            title="Prompt"
          >
            <Grid container spacing={2} sx={{ width: "100%" }}>
              <Grid size={12}>
                <ConductorInput
                  label="Prompt"
                  name="prompt"
                  value={(get("inputParameters.prompt") as string) || ""}
                  onTextInputChange={(v) => set("inputParameters.prompt", v)}
                  multiline
                  rows={5}
                  fullWidth
                  placeholder="Describe the image to generate"
                />
              </Grid>
            </Grid>
          </TaskFormSection>

          <TaskFormSection title="Image options">
            <Grid container spacing={2} sx={{ width: "100%" }}>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Number of images (n)"
                  value={get("inputParameters.n") as number}
                  coerceTo="integer"
                  onChange={(v) => set("inputParameters.n", v)}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Width"
                  value={get("inputParameters.width") as number}
                  coerceTo="integer"
                  onChange={(v) => set("inputParameters.width", v)}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Height"
                  value={get("inputParameters.height") as number}
                  coerceTo="integer"
                  onChange={(v) => set("inputParameters.height", v)}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Size"
                  value={get("inputParameters.size") as string}
                  onChange={(v) => set("inputParameters.size", v)}
                  placeholder="1024x1024"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Style"
                  value={get("inputParameters.style") as string}
                  onChange={(v) => set("inputParameters.style", v)}
                  placeholder="vivid / natural"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Output format"
                  value={get("inputParameters.outputFormat") as string}
                  onChange={(v) => set("inputParameters.outputFormat", v)}
                  placeholder="png / jpg / webp"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Weight"
                  value={get("inputParameters.weight") as number}
                  coerceTo="double"
                  onChange={(v) => set("inputParameters.weight", v)}
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
