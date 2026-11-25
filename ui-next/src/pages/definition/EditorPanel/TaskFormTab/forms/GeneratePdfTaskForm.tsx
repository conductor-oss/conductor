import { Box, Grid } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import ConductorInput from "components/ui/inputs/ConductorInput";
import { path as _path } from "lodash/fp";
import { updateField } from "utils/fieldHelpers";
import { ConductorAdditionalHeadersBase } from "./HTTPTaskForm/ConductorAdditionalHeaders";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

/** Config form for GENERATE_PDF — converts markdown content to a styled PDF document. */
export const GeneratePdfTaskForm = ({ task, onChange }: TaskFormProps) => {
  const get = (p: string) => _path(p, task);
  const set = (p: string, value: any) => onChange(updateField(p, value, task));

  const pdfMetadata: Record<string, string> =
    (get("inputParameters.pdfMetadata") as Record<string, string>) || {};

  return (
    <Box padding={1} width="100%">
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Content"
      >
        <Grid container spacing={2} sx={{ width: "100%" }}>
          <Grid size={12}>
            <ConductorInput
              label="Markdown"
              name="markdown"
              value={(get("inputParameters.markdown") as string) || ""}
              onTextInputChange={(v) => set("inputParameters.markdown", v)}
              multiline
              rows={10}
              fullWidth
              placeholder="# Markdown content to render as a PDF"
            />
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection title="Page">
        <Grid container spacing={2} sx={{ width: "100%" }}>
          <Grid size={{ xs: 12, md: 4 }}>
            <ConductorAutocompleteVariables
              label="Page size"
              value={get("inputParameters.pageSize") as string}
              onChange={(v) => set("inputParameters.pageSize", v)}
              otherOptions={["A4", "LETTER", "LEGAL"]}
              placeholder="A4"
            />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <ConductorAutocompleteVariables
              label="Theme"
              value={get("inputParameters.theme") as string}
              onChange={(v) => set("inputParameters.theme", v)}
              otherOptions={["default", "compact"]}
              placeholder="default"
            />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <ConductorAutocompleteVariables
              label="Base font size"
              value={get("inputParameters.baseFontSize") as number}
              coerceTo="double"
              onChange={(v) => set("inputParameters.baseFontSize", v)}
            />
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection title="Margins (points)">
        <Grid container spacing={2} sx={{ width: "100%" }}>
          <Grid size={{ xs: 6, md: 3 }}>
            <ConductorAutocompleteVariables
              label="Top"
              value={get("inputParameters.marginTop") as number}
              coerceTo="double"
              onChange={(v) => set("inputParameters.marginTop", v)}
            />
          </Grid>
          <Grid size={{ xs: 6, md: 3 }}>
            <ConductorAutocompleteVariables
              label="Right"
              value={get("inputParameters.marginRight") as number}
              coerceTo="double"
              onChange={(v) => set("inputParameters.marginRight", v)}
            />
          </Grid>
          <Grid size={{ xs: 6, md: 3 }}>
            <ConductorAutocompleteVariables
              label="Bottom"
              value={get("inputParameters.marginBottom") as number}
              coerceTo="double"
              onChange={(v) => set("inputParameters.marginBottom", v)}
            />
          </Grid>
          <Grid size={{ xs: 6, md: 3 }}>
            <ConductorAutocompleteVariables
              label="Left"
              value={get("inputParameters.marginLeft") as number}
              coerceTo="double"
              onChange={(v) => set("inputParameters.marginLeft", v)}
            />
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection title="Document metadata">
        <Grid container spacing={2} sx={{ width: "100%" }}>
          <Grid size={12}>
            <ConductorAdditionalHeadersBase
              headers={pdfMetadata}
              onChangeHeaders={(m) => set("inputParameters.pdfMetadata", m)}
            />
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection title="Output">
        <Grid container spacing={2} sx={{ width: "100%" }}>
          <Grid size={{ xs: 12, md: 6 }}>
            <ConductorAutocompleteVariables
              label="Output location"
              value={get("inputParameters.outputLocation") as string}
              onChange={(v) => set("inputParameters.outputLocation", v)}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <ConductorAutocompleteVariables
              label="Image base URL"
              value={get("inputParameters.imageBaseUrl") as string}
              onChange={(v) => set("inputParameters.imageBaseUrl", v)}
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
  );
};
