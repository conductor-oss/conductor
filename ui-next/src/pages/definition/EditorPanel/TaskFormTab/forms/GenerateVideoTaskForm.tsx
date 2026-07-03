import { Box, FormControlLabel, Grid, Switch } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { path as _path } from "lodash/fp";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { fieldsToFieldsFieldsComponents, updateField } from "utils/fieldHelpers";
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

/**
 * Config form for GENERATE_VIDEO — generates video from a prompt/image using an LLM provider.
 * The task polls asynchronously for completion via its output jobId.
 */
export const GenerateVideoTaskForm = ({ task, onChange }: TaskFormProps) => {
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
                  placeholder="Describe the video to generate"
                />
              </Grid>
              <Grid size={12}>
                <ConductorAutocompleteVariables
                  label="Input image (optional)"
                  value={get("inputParameters.inputImage") as string}
                  onChange={(v) => set("inputParameters.inputImage", v)}
                />
              </Grid>
            </Grid>
          </TaskFormSection>

          <TaskFormSection title="Dimensions">
            <Grid container spacing={2} sx={{ width: "100%" }}>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Aspect ratio"
                  value={get("inputParameters.aspectRatio") as string}
                  onChange={(v) => set("inputParameters.aspectRatio", v)}
                  placeholder="16:9"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Resolution"
                  value={get("inputParameters.resolution") as string}
                  onChange={(v) => set("inputParameters.resolution", v)}
                  placeholder="1080p"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Size"
                  value={get("inputParameters.size") as string}
                  onChange={(v) => set("inputParameters.size", v)}
                  placeholder="1280x720"
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
                  label="FPS"
                  value={get("inputParameters.fps") as number}
                  coerceTo="integer"
                  onChange={(v) => set("inputParameters.fps", v)}
                />
              </Grid>
            </Grid>
          </TaskFormSection>

          <TaskFormSection title="Output">
            <Grid container spacing={2} sx={{ width: "100%" }}>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Duration (seconds)"
                  value={get("inputParameters.duration") as number}
                  coerceTo="integer"
                  onChange={(v) => set("inputParameters.duration", v)}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Output format"
                  value={get("inputParameters.outputFormat") as string}
                  onChange={(v) => set("inputParameters.outputFormat", v)}
                  placeholder="mp4"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Seed"
                  value={get("inputParameters.seed") as number}
                  coerceTo="integer"
                  onChange={(v) => set("inputParameters.seed", v)}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Number of outputs (n)"
                  value={get("inputParameters.n") as number}
                  coerceTo="integer"
                  onChange={(v) => set("inputParameters.n", v)}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Thumbnail timestamp (seconds)"
                  value={get("inputParameters.thumbnailTimestamp") as number}
                  coerceTo="double"
                  onChange={(v) => set("inputParameters.thumbnailTimestamp", v)}
                />
              </Grid>
              <Grid size={12}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={!!get("inputParameters.generateAudio")}
                      onChange={(e) =>
                        set("inputParameters.generateAudio", e.target.checked)
                      }
                    />
                  }
                  label="Generate audio"
                />
                <FormControlLabel
                  control={
                    <Switch
                      checked={!!get("inputParameters.generateThumbnail")}
                      onChange={(e) =>
                        set(
                          "inputParameters.generateThumbnail",
                          e.target.checked,
                        )
                      }
                    />
                  }
                  label="Generate thumbnail"
                />
              </Grid>
            </Grid>
          </TaskFormSection>

          <TaskFormSection title="Advanced">
            <Grid container spacing={2} sx={{ width: "100%" }}>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Style"
                  value={get("inputParameters.style") as string}
                  onChange={(v) => set("inputParameters.style", v)}
                  placeholder="cinematic / animated"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Motion"
                  value={get("inputParameters.motion") as string}
                  onChange={(v) => set("inputParameters.motion", v)}
                  placeholder="slow / medium / fast"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <ConductorAutocompleteVariables
                  label="Guidance scale"
                  value={get("inputParameters.guidanceScale") as number}
                  coerceTo="double"
                  onChange={(v) => set("inputParameters.guidanceScale", v)}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <ConductorAutocompleteVariables
                  label="Person generation"
                  value={get("inputParameters.personGeneration") as string}
                  onChange={(v) => set("inputParameters.personGeneration", v)}
                  placeholder="dont_allow / allow_adult"
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <ConductorAutocompleteVariables
                  label="Max duration (seconds)"
                  value={get("inputParameters.maxDurationSeconds") as number}
                  coerceTo="integer"
                  onChange={(v) => set("inputParameters.maxDurationSeconds", v)}
                />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <ConductorAutocompleteVariables
                  label="Max cost (dollars)"
                  value={get("inputParameters.maxCostDollars") as number}
                  coerceTo="double"
                  onChange={(v) => set("inputParameters.maxCostDollars", v)}
                />
              </Grid>
              <Grid size={12}>
                <ConductorInput
                  label="Negative prompt"
                  name="negativePrompt"
                  value={
                    (get("inputParameters.negativePrompt") as string) || ""
                  }
                  onTextInputChange={(v) =>
                    set("inputParameters.negativePrompt", v)
                  }
                  multiline
                  rows={3}
                  fullWidth
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
