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

const connectionIdPath = "inputParameters.connectionId";
const folderIdsPath = "inputParameters.folderIds";
const fileIdsPath = "inputParameters.fileIds";
const maxFilesPath = "inputParameters.maxFiles";
const mimeTypesPath = "inputParameters.mimeTypes";

export const GDriveReadTaskForm = ({ task, onChange }: TaskFormProps) => {
  const connectionId = _path(connectionIdPath, task);
  const folderIds = _path(folderIdsPath, task);
  const fileIds = _path(fileIdsPath, task);
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
              value={connectionId}
              onChange={(changes) =>
                onChange(updateField(connectionIdPath, changes, task))
              }
              label="Connection ID"
              inputProps={{
                tooltip: {
                  title: "Connection ID",
                  content:
                    "Optional stored Google Drive connection ID, or a workflow input expression. When omitted, the task uses the latest saved connection.",
                },
              }}
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              fullWidth
              value={folderIds}
              onChange={(changes) =>
                onChange(updateField(folderIdsPath, changes, task))
              }
              label="Folder IDs or URLs"
              inputProps={{
                tooltip: {
                  title: "Folder IDs or URLs",
                  content:
                    "Optional Google Drive folder IDs, folder URLs, comma-separated values, or a workflow input expression. Leave blank with no file IDs to read from the whole Drive.",
                },
              }}
            />
          </Grid>
          <Grid size={12}>
            <ConductorAutocompleteVariables
              fullWidth
              value={fileIds}
              onChange={(changes) =>
                onChange(updateField(fileIdsPath, changes, task))
              }
              label="File IDs or URLs"
              inputProps={{
                tooltip: {
                  title: "File IDs or URLs",
                  content:
                    "Optional Google Drive file IDs, file URLs, comma-separated values, or a workflow input expression.",
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
                      "Optional list of MIME types to keep from the Google Drive response.",
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
