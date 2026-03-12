import { Box, Grid, Typography } from "@mui/material";
import { path as _path, pipe as _pipe, assoc as _assoc } from "lodash/fp";
import { useState } from "react";

import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorFlatMapForm } from "components/v1/FlatMapForm/ConductorFlatMapForm";
import { TaskType } from "types";
import { updateField } from "utils/fieldHelpers";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import { Optional } from "./OptionalFieldForm";
import { ConductorAutoComplete } from "components/v1";
import { useGetIntegration } from "utils/hooks";
import { MaybeVariable } from "./MaybeVariable";

const DEFAULT_VALUES_FOR_FILE_TYPES_ARRAY = [
  "java",
  "xls",
  "csv",
  "pdf",
  "All",
];
const integrationNamePath = "inputParameters.integrationName";
const inputLocationPath = "inputParameters.inputLocation";
const outputLocationPath = "inputParameters.outputLocation";
const fileTypesPath = "inputParameters.fileTypes";
const integrationNamesPath = "inputParameters.integrationNames";

// Helper function to validate URL format for web-based inputs
const validateWebUrl = (value: string): string | null => {
  // Skip validation for variable references
  if (value.includes("${") || value.includes("$.")) {
    return null;
  }

  // Check for cloud storage protocols first
  if (value.startsWith("s3://")) {
    // Validate S3 URL format: s3://bucketname/folder
    const s3Path = value.substring(5); // Remove "s3://"
    if (!s3Path || s3Path.length === 0) {
      return "Invalid S3 URL: Missing bucket name. Example: s3://bucketname/folder";
    }
    if (s3Path.includes("//")) {
      return "Invalid S3 URL: Double slashes not allowed. Example: s3://bucketname/folder";
    }
    return null;
  }

  if (value.startsWith("gs://")) {
    // Validate Google Cloud Storage URL format: gs://path
    const gsPath = value.substring(5); // Remove "gs://"
    if (!gsPath || gsPath.length === 0) {
      return "Invalid GCS URL: Missing path. Example: gs://path";
    }
    if (gsPath.includes("//")) {
      return "Invalid GCS URL: Double slashes not allowed. Example: gs://path";
    }
    return null;
  }

  if (value.startsWith("azureblob://")) {
    // Validate Azure Blob Storage URL format: azureblob://path
    const azurePath = value.substring(12); // Remove "azureblob://"
    if (!azurePath || azurePath.length === 0) {
      return "Invalid Azure Blob URL: Missing path. Example: azureblob://path";
    }
    if (azurePath.includes("//")) {
      return "Invalid Azure Blob URL: Double slashes not allowed. Example: azureblob://path";
    }
    return null;
  }

  // Check if it's a web-based URL (http/https)
  try {
    const url = new URL(value);
    // Validate that the URL has a valid hostname
    if (!url.hostname || url.hostname.length === 0) {
      return "Invalid URL: Missing hostname";
    }
    // Check for proper URL structure
    if (url.protocol !== "http:" && url.protocol !== "https:") {
      return "Invalid URL: Only http://, https://, s3://, gs://, and azureblob:// protocols are supported";
    }
  } catch {
    return "Invalid URL format. Examples: https://example.com/path, s3://bucketname/folder, gs://bucketname/folder, azureblob://container/path";
  }

  return null;
};

export const ListFilesTaskForm = ({ task, onChange }: TaskFormProps) => {
  const integrationName = _path(integrationNamePath, task);
  const inputLocation = _path(inputLocationPath, task);
  const outputLocation = _path(outputLocationPath, task);
  const fileTypes = _path(fileTypesPath, task);
  const integrationNames = _path(integrationNamesPath, task);

  const [inputLocationError, setInputLocationError] = useState<string | null>(
    null,
  );

  // need to fetch compatible integration names and pass them to the integrationName autocomplete options
  const integrations = useGetIntegration({});

  // Filter integrations to only include git, aws, and gcp types
  const integrationNameOptions =
    integrations?.data
      ?.filter((integration) => {
        const type = integration?.type?.toLowerCase() || "";
        // Filter by type containing git, aws, or gcp
        return type === "git" || type === "aws" || type === "gcp";
      })
      ?.map((integration) => integration?.name) || [];

  return (
    <Box>
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Configuration"
      >
        <Grid container spacing={3} pt={3}>
          <Grid size={12}>
            <Grid container spacing={3}>
              <Grid size={12}>
                <ConductorAutocompleteVariables
                  value={integrationName}
                  label="Integration Name"
                  otherOptions={integrationNameOptions}
                  onChange={(changes) =>
                    onChange(updateField(integrationNamePath, changes, task))
                  }
                />
              </Grid>
            </Grid>
          </Grid>
          <Grid size={12}>
            <Grid container spacing={3} alignItems={"center"}>
              <Grid size={12}>
                <ConductorAutocompleteVariables
                  fullWidth
                  required
                  value={inputLocation}
                  onChange={(changes) => {
                    // Validate URL format for web-based inputs
                    const error = validateWebUrl(changes);
                    setInputLocationError(error);
                    onChange(updateField(inputLocationPath, changes, task));
                  }}
                  label="Input Location"
                  error={!!inputLocationError || !inputLocation}
                  helperText={inputLocationError || undefined}
                  inputProps={{
                    tooltip: {
                      title: "Input Location",
                      content: (
                        <div>
                          <Typography>
                            Location of files to be indexed.
                          </Typography>
                          <Typography>
                            <strong>Examples based on integration type:</strong>
                          </Typography>
                          <Typography style={{ margin: "8px 0 4px" }}>
                            <strong>Cloud Storage:</strong>
                          </Typography>
                          <ul style={{ margin: "4px 0" }}>
                            <li>s3://bucketname/folder</li>
                            <li>gs://path</li>
                            <li>azureblob://path</li>
                          </ul>
                          <Typography style={{ margin: "8px 0 4px" }}>
                            <strong>Git Repositories:</strong>
                          </Typography>
                          <ul style={{ margin: "4px 0" }}>
                            <li>https://github.com/owner/repo</li>
                            <li>https://gitlab.com/owner/repo</li>
                          </ul>
                          <Typography style={{ margin: "8px 0 4px" }}>
                            <strong>Website Sitemap:</strong>
                          </Typography>
                          <ul style={{ margin: "4px 0" }}>
                            <li>
                              https://example.com/sitemap.xml (full path
                              required)
                            </li>
                          </ul>
                          <Typography style={{ margin: "8px 0 4px" }}>
                            <strong>Single Page:</strong>
                          </Typography>
                          <ul style={{ margin: "4px 0" }}>
                            <li>https://example.com/page.html</li>
                          </ul>
                        </div>
                      ),
                    },
                  }}
                />
              </Grid>
              <Grid size={12}>
                <MaybeVariable
                  value={fileTypes}
                  onChange={(val) => {
                    onChange(updateField(fileTypesPath, val, task));
                  }}
                  path={fileTypesPath}
                  taskType={TaskType.LIST_FILES}
                  helperTextStyle={{ padding: 0 }}
                  fieldStyle={{ paddingX: 0 }}
                >
                  <ConductorAutoComplete
                    fullWidth
                    label="File Types"
                    options={DEFAULT_VALUES_FOR_FILE_TYPES_ARRAY}
                    multiple
                    freeSolo
                    onChange={(__, val: string[]) => {
                      // Validate and sanitize file types: remove dots and convert to lowercase
                      const sanitizedFileTypes = val.map((fileType) =>
                        fileType.replace(/\./g, "").toLowerCase(),
                      );
                      onChange(
                        updateField(fileTypesPath, sanitizedFileTypes, task),
                      );
                    }}
                    value={fileTypes}
                    conductorInputProps={{
                      tooltip: {
                        title: "Field Name",
                        content:
                          "List of file types to include (e.g., java, xls, csv, pdf, etc.). File types should be lowercase without dots.",
                      },
                    }}
                  />
                </MaybeVariable>
              </Grid>
            </Grid>
          </Grid>
          <Grid size={12}>
            <Grid container spacing={3}>
              <Grid size={12}>
                <ConductorAutocompleteVariables
                  fullWidth
                  value={outputLocation}
                  onChange={(changes) =>
                    onChange(updateField(outputLocationPath, changes, task))
                  }
                  label="Output Location"
                  inputProps={{
                    tooltip: {
                      title: "Output Location",
                      content:
                        "Location to store the output list of files as a text file.",
                    },
                  }}
                />
              </Grid>
            </Grid>
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Advanced Integration Configuration"
      >
        <MaybeVariable
          value={integrationNames}
          onChange={(val) => {
            onChange(updateField(integrationNamesPath, val, task));
          }}
          path={integrationNamesPath}
          taskType={TaskType.LIST_FILES}
          helperTextStyle={{ padding: 0 }}
          fieldStyle={{ paddingX: 0 }}
        >
          <>
            Map of integration types to integration names for multiple
            integrations
          </>
          <Grid container>
            <Grid size={12}>
              <ConductorFlatMapForm
                keyColumnLabel="Type"
                valueColumnLabel="Name"
                addItemLabel="Add Integration"
                onChange={(changes) =>
                  onChange(updateField(integrationNamesPath, changes, task))
                }
                value={integrationNames}
                taskType={TaskType.LIST_FILES}
                path={integrationNamesPath}
                otherOptions={integrationNameOptions}
              />
            </Grid>
          </Grid>
        </MaybeVariable>
      </TaskFormSection>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Box mt={3}>
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
