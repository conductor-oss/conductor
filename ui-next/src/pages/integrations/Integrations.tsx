import {
  Alert,
  Box,
  Button,
  Chip,
  Grid,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import CloudSyncOutlinedIcon from "@mui/icons-material/CloudSyncOutlined";
import SectionHeader from "components/layout/SectionHeader";
import { CodeSnippet } from "components/ui/CodeSnippet";
import Header from "components/ui/Header";
import SectionContainer from "components/ui/layout/SectionContainer";
import { useMemo, useState } from "react";
import { Helmet } from "react-helmet";
import { useActionWithPath } from "utils/query";
import { getErrorMessage } from "utils/utils";

type GDriveFile = {
  id: string;
  name: string;
  mimeType: string;
  size?: string;
  modifiedTime?: string;
  webViewLink?: string;
};

type GDriveLoadResponse = {
  folderId: string;
  count: number;
  files: GDriveFile[];
};

const DEFAULT_MAX_FILES = 25;

export default function Integrations() {
  const [folderId, setFolderId] = useState("");
  const [oauthTokenJson, setOauthTokenJson] = useState("");
  const [maxFiles, setMaxFiles] = useState(DEFAULT_MAX_FILES);
  const [result, setResult] = useState<GDriveLoadResponse | null>(null);
  const [error, setError] = useState("");

  const taskSnippet = useMemo(
    () =>
      JSON.stringify(
        {
          name: "GDRIVE_READ",
          taskReferenceName: "gdrive_read_ref",
          type: "GDRIVE_READ",
          inputParameters: {
            folderId: "${workflow.input.driveFolderId}",
            oauthTokenJson: "${workflow.input.oauthTokenJson}",
            maxFiles,
          },
        },
        null,
        2,
      ),
    [maxFiles],
  );

  const loadGDriveAction = useActionWithPath<GDriveLoadResponse>({
    onSuccess: (data) => {
      setResult(data);
      setError("");
    },
    onError: async (response: Response) => {
      const message = await getErrorMessage(response);
      setError(message || "Unable to load Google Drive folder.");
    },
  });

  const handleLoad = () => {
    setError("");
    setResult(null);

    if (!folderId.trim()) {
      setError("Folder ID is required.");
      return;
    }

    try {
      JSON.parse(oauthTokenJson);
    } catch {
      setError("OAuth token JSON is invalid.");
      return;
    }

    loadGDriveAction.mutate({
      path: "/integrations/gdrive/load",
      method: "post",
      body: JSON.stringify({
        folderId: folderId.trim(),
        oauthTokenJson,
        maxFiles,
      }),
    });
  };

  return (
    <>
      <Helmet>
        <title>Integrations</title>
      </Helmet>
      <Header loading={loadGDriveAction.isLoading} />
      <SectionContainer header={<SectionHeader title="Integrations" />}>
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <Paper variant="outlined" sx={{ p: 3 }}>
              <Stack spacing={3}>
                <Stack direction="row" alignItems="center" spacing={1}>
                  <Typography variant="h6">Google Drive</Typography>
                  <Chip label="GDRIVE_READ" size="small" />
                </Stack>
                <TextField
                  label="Folder ID"
                  value={folderId}
                  onChange={(event) => setFolderId(event.target.value)}
                  fullWidth
                  size="small"
                />
                <TextField
                  label="OAuth Token JSON"
                  value={oauthTokenJson}
                  onChange={(event) => setOauthTokenJson(event.target.value)}
                  fullWidth
                  multiline
                  minRows={10}
                />
                <TextField
                  label="Max Files"
                  value={maxFiles}
                  onChange={(event) =>
                    setMaxFiles(Math.max(1, Number(event.target.value) || 1))
                  }
                  fullWidth
                  size="small"
                  type="number"
                />
                <Button
                  variant="contained"
                  onClick={handleLoad}
                  disabled={loadGDriveAction.isLoading}
                  startIcon={<CloudSyncOutlinedIcon />}
                >
                  Load Drive
                </Button>
                {error && <Alert severity="error">{error}</Alert>}
              </Stack>
            </Paper>
          </Grid>

          <Grid size={{ xs: 12, lg: 7 }}>
            <Stack spacing={3}>
              <Paper variant="outlined" sx={{ p: 3 }}>
                <Stack spacing={2}>
                  <Typography variant="h6">Task</Typography>
                  <CodeSnippet code={taskSnippet} className="json" />
                </Stack>
              </Paper>

              <Paper variant="outlined" sx={{ p: 3 }}>
                <Stack spacing={2}>
                  <Stack direction="row" justifyContent="space-between">
                    <Typography variant="h6">Files</Typography>
                    {result && <Chip label={`${result.count} loaded`} />}
                  </Stack>
                  {result?.files?.length ? (
                    <TableContainer component={Box}>
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Name</TableCell>
                            <TableCell>Mime Type</TableCell>
                            <TableCell>Modified</TableCell>
                            <TableCell>ID</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {result.files.map((file) => (
                            <TableRow key={file.id}>
                              <TableCell>
                                {file.webViewLink ? (
                                  <a
                                    href={file.webViewLink}
                                    target="_blank"
                                    rel="noreferrer"
                                  >
                                    {file.name}
                                  </a>
                                ) : (
                                  file.name
                                )}
                              </TableCell>
                              <TableCell>{file.mimeType}</TableCell>
                              <TableCell>{file.modifiedTime || "-"}</TableCell>
                              <TableCell>{file.id}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  ) : (
                    <Typography color="text.secondary">
                      No files loaded.
                    </Typography>
                  )}
                </Stack>
              </Paper>
            </Stack>
          </Grid>
        </Grid>
      </SectionContainer>
    </>
  );
}
