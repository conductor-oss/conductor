import { Box, Grid } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorAutoComplete } from "components/ui/inputs";
import { path as _path } from "lodash/fp";
import { TaskType } from "types";
import { updateField } from "utils/fieldHelpers";
import { MaybeVariable } from "./MaybeVariable";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

const DEFAULT_MIME_TYPES = [
  "application/pdf",
  "image/jpeg",
  "image/png",
  "image/tiff",
  "image/webp",
  "text/csv",
  "application/vnd.ms-excel",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
];

const folderIdPath = "inputParameters.folderId";
const oauthTokenJsonPath = "inputParameters.oauthTokenJson";
const maxFilesPath = "inputParameters.maxFiles";
const mimeTypesPath = "inputParameters.mimeTypes";

export const GDriveReadTaskForm = ({ task, onChange }: TaskFormProps) => {
  const folderId = _path(folderIdPath, task);
  const oauthTokenJson = _path(oauthTokenJsonPath, task);
  const maxFiles = _path(maxFilesPath, task);
  const mimeTypes = _path(mimeTypesPath, task);

  return (
    <Box>
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="Google Drive"
      >
        <Grid container spacing={3} pt={3}>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              fullWidth
              required
              value={folderId}
              onChange={(changes) =>
                onChange(updateField(folderIdPath, changes, task))
              }
              label="Folder ID or URL"
              inputProps={{
                tooltip: {
                  title: "Folder ID or URL",
                  content:
                    "Google Drive folder ID, folder URL, or a workflow input expression.",
                },
              }}
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              fullWidth
              required
              value={oauthTokenJson}
              onChange={(changes) =>
                onChange(updateField(oauthTokenJsonPath, changes, task))
              }
              label="OAuth Token JSON"
              inputProps={{
                tooltip: {
                  title: "OAuth Token JSON",
                  content:
                    "OAuth token JSON or a workflow input expression containing access_token/token, or refresh_token with client_id and client_secret.",
                },
              }}
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              fullWidth
              value={maxFiles}
              coerceTo="integer"
              onChange={(changes) =>
                onChange(updateField(maxFilesPath, changes, task))
              }
              label="Max Files"
              inputProps={{
                tooltip: {
                  title: "Max Files",
                  content:
                    "Maximum number of file metadata records to return. Defaults to 100 when omitted.",
                },
              }}
            />
          </Grid>
          <Grid size={12}>
            <MaybeVariable
              value={mimeTypes}
              onChange={(changes) =>
                onChange(updateField(mimeTypesPath, changes, task))
              }
              path={mimeTypesPath}
              taskType={TaskType.GDRIVE_READ}
              helperTextStyle={{ padding: 0 }}
              fieldStyle={{ paddingX: 0 }}
            >
              <ConductorAutoComplete
                fullWidth
                label="MIME Types"
                options={DEFAULT_MIME_TYPES}
                multiple
                freeSolo
                onChange={(__, value: string[]) =>
                  onChange(updateField(mimeTypesPath, value, task))
                }
                value={mimeTypes ?? []}
                conductorInputProps={{
                  tooltip: {
                    title: "MIME Types",
                    content:
                      "Optional list of MIME types to keep from the Google Drive folder response.",
                  },
                }}
              />
            </MaybeVariable>
          </Grid>
        </Grid>
      </TaskFormSection>
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Box mt={3}>
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
