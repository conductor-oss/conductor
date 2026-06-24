import {
  Alert,
  Box,
  Button,
  Chip,
  Grid,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import CloudSyncOutlinedIcon from "@mui/icons-material/CloudSyncOutlined";
import CloudUploadOutlinedIcon from "@mui/icons-material/CloudUploadOutlined";
import SectionHeader from "components/layout/SectionHeader";
import { CodeSnippet } from "components/ui/CodeSnippet";
import Header from "components/ui/Header";
import SectionContainer from "components/ui/layout/SectionContainer";
import { ChangeEvent, useMemo, useState } from "react";
import { Helmet } from "react-helmet";
import { useActionWithPath, useFetch } from "utils/query";
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
  folderId?: string;
  folderIds: string[];
  fileIds: string[];
  count: number;
  files: GDriveFile[];
};

type GDriveConnectionResponse = {
  connectionId: string;
  accountName?: string;
};

const DEFAULT_MAX_FILES = 25;

const createConnectionId = () => {
  if (window.crypto?.randomUUID) {
    return `gdrive-${window.crypto.randomUUID()}`;
  }
  return `gdrive-${Date.now().toString(36)}-${Math.random()
    .toString(36)
    .slice(2, 10)}`;
};

const safeParseJson = (value: string) => {
  if (!value.trim()) {
    return null;
  }
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
};

const getOAuthClient = (jsonText: string) => {
  const json = safeParseJson(jsonText);
  if (!json) {
    return {};
  }

  const client = json.installed || json.web || json;
  return {
    clientId: client.client_id || json.client_id || "",
    clientSecret: client.client_secret || json.client_secret || "",
  };
};

const buildOAuthClientJson = (clientId: string, clientSecret: string) =>
  JSON.stringify({
    installed: {
      client_id: clientId,
      client_secret: clientSecret,
      auth_uri: "https://accounts.google.com/o/oauth2/auth",
      token_uri: "https://oauth2.googleapis.com/token",
    },
  });

const workflowInput = (name: string) => `\${workflow.input.${name}}`;

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;

const normalizeErrorMessage = (
  message: string,
  fallback: string,
  status?: number,
) => {
  const value = String(message || "").trim();
  if (!value) {
    return fallback;
  }

  if (/<html[\s>]/i.test(value) || /<body[\s>]/i.test(value)) {
    const heading = value.match(/<h1[^>]*>(.*?)<\/h1>/i);
    const title = value.match(/<title[^>]*>(.*?)<\/title>/i);
    const label = (heading && heading[1]) || (title && title[1]);
    if (label) {
      return `Request failed: ${label.replace(/\s+/g, " ").trim()}.`;
    }
    if (status) {
      return `Request failed with HTTP ${status}.`;
    }
    return "Request failed. The server returned an HTML error page.";
  }

  return value;
};

export default function Integrations() {
  const [connectionId, setConnectionId] = useState(createConnectionId);
  const [accountName, setAccountName] = useState("");
  const [clientId, setClientId] = useState("");
  const [clientSecret, setClientSecret] = useState("");
  const [oauthTokenJson, setOauthTokenJson] = useState("");
  const [clientJsonFileName, setClientJsonFileName] = useState("");
  const [tokenJsonFileName, setTokenJsonFileName] = useState("");
  const [maxFiles, setMaxFiles] = useState(DEFAULT_MAX_FILES);
  const [result, setResult] = useState<GDriveLoadResponse | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [activeTab, setActiveTab] = useState("create");

  const {
    data: connections = [],
    refetch: refetchConnections,
    isFetching: isFetchingConnections,
  } = useFetch<GDriveConnectionResponse[]>("/integrations/gdrive/connections");

  const taskSnippet = useMemo(
    () =>
      JSON.stringify(
        {
          name: "read_g_drive",
          taskReferenceName: "read_g_drive_ref",
          type: "GDRIVE_READ",
          inputParameters: {
            connectionId: workflowInput("gdriveConnectionId"),
            folderIds: workflowInput("driveFolderIds"),
            fileIds: workflowInput("driveFileIds"),
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
      setError(
        normalizeErrorMessage(
          message,
          "Unable to load Google Drive data.",
          response.status,
        ),
      );
    },
  });

  const saveGDriveConnectionAction =
    useActionWithPath<GDriveConnectionResponse>({
      onSuccess: (data) => {
        setConnectionId(data.connectionId || connectionId);
        setAccountName(
          data.accountName || accountName || data.connectionId || connectionId,
        );
        setMessage(
          `Google Drive connection ${data.connectionId || connectionId} created.`,
        );
        refetchConnections();
        setError("");
      },
      onError: async (response: Response) => {
        const message = await getErrorMessage(response);
        setError(
          normalizeErrorMessage(
            message,
            "Unable to save Google Drive connection.",
            response.status,
          ),
        );
      },
    });

  const deleteGDriveConnectionAction = useActionWithPath({
    onSuccess: (_, variables) => {
      const deletedConnectionId = variables.path.split("/").pop() || "";
      setMessage(
        `Google Drive connection ${decodeURIComponent(deletedConnectionId)} deleted.`,
      );
      if (connectionId === decodeURIComponent(deletedConnectionId)) {
        setConnectionId(createConnectionId());
        setAccountName("");
      }
      refetchConnections();
      setError("");
    },
    onError: async (response: Response) => {
      const message = await getErrorMessage(response);
      setError(
        normalizeErrorMessage(
          message,
          "Unable to delete Google Drive connection.",
          response.status,
        ),
      );
    },
  });

  const handleSaveConnection = () => {
    setError("");
    setMessage("");

    const nextConnectionId = connectionId.trim() || createConnectionId();
    const nextAccountName = accountName.trim() || nextConnectionId;
    setConnectionId(nextConnectionId);
    setAccountName(nextAccountName);

    if (!safeParseJson(oauthTokenJson)) {
      setError("OAuth token JSON is invalid.");
      return;
    }

    saveGDriveConnectionAction.mutate({
      path: "/integrations/gdrive/connections",
      method: "post",
      body: JSON.stringify({
        connectionId: nextConnectionId,
        accountName: nextAccountName,
        oauthTokenJson,
        oauthClientJson:
          clientId.trim() && clientSecret.trim()
            ? buildOAuthClientJson(clientId.trim(), clientSecret.trim())
            : undefined,
      }),
    });
  };

  const handleLoad = () => {
    setError("");
    setMessage("");
    setResult(null);

    loadGDriveAction.mutate({
      path: "/integrations/gdrive/load",
      method: "post",
      body: JSON.stringify({
        connectionId: connectionId.trim() || undefined,
        maxFiles,
      }),
    });
  };

  const handleDeleteConnection = (targetConnectionId: string) => {
    deleteGDriveConnectionAction.mutate({
      path: `/integrations/gdrive/connections/${encodeURIComponent(
        targetConnectionId,
      )}`,
      method: "delete",
    });
  };

  const handleUseConnection = (connection: GDriveConnectionResponse) => {
    setConnectionId(connection.connectionId);
    setAccountName(connection.accountName || connection.connectionId);
    setActiveTab("create");
  };

  const handleClientJsonFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    try {
      const content = await file.text();
      JSON.parse(content);
      const client = getOAuthClient(content);
      if (!client.clientId || !client.clientSecret) {
        throw new Error(
          "Client JSON must include client_id and client_secret.",
        );
      }
      setClientId(client.clientId);
      setClientSecret(client.clientSecret);
      setClientJsonFileName(file.name);
      setMessage(`${file.name} loaded as OAuth client JSON.`);
      setError("");
    } catch (err) {
      setError(errorText(err, "OAuth client JSON file is invalid."));
    } finally {
      event.target.value = "";
    }
  };

  const handleTokenJsonFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    try {
      const content = await file.text();
      const parsed = JSON.parse(content);
      if (!(parsed.access_token || parsed.token || parsed.refresh_token)) {
        throw new Error(
          "OAuth token JSON must include access_token, token, or refresh_token.",
        );
      }
      const client = getOAuthClient(content);
      if (client.clientId) {
        setClientId(client.clientId);
      }
      if (client.clientSecret) {
        setClientSecret(client.clientSecret);
      }
      setOauthTokenJson(content);
      setTokenJsonFileName(file.name);
      setMessage(`${file.name} loaded as OAuth token JSON.`);
      setError("");
    } catch (err) {
      setError(errorText(err, "OAuth token JSON file is invalid."));
    } finally {
      event.target.value = "";
    }
  };

  return (
    <>
      <Helmet>
        <title>Integrations</title>
      </Helmet>
      <Header
        loading={
          loadGDriveAction.isLoading ||
          saveGDriveConnectionAction.isLoading ||
          deleteGDriveConnectionAction.isLoading
        }
      />
      <SectionContainer header={<SectionHeader title="Integrations" />}>
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <Paper variant="outlined" sx={{ p: 3 }}>
              <Stack direction="row" spacing={3} alignItems="stretch">
                <Tabs
                  orientation="vertical"
                  value={activeTab}
                  onChange={(_, value) => setActiveTab(value)}
                  sx={{ borderRight: 1, borderColor: "divider", minWidth: 120 }}
                >
                  <Tab value="create" label="Create" />
                  <Tab value="manage" label="Manage" />
                </Tabs>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  {activeTab === "create" && (
                    <Stack spacing={3}>
                      <Stack direction="row" alignItems="center" spacing={1}>
                        <Typography variant="h6">Google Drive</Typography>
                        <Chip label="GDRIVE_READ" size="small" />
                      </Stack>
                      <TextField
                        label="Connection ID"
                        value={connectionId}
                        onChange={(event) =>
                          setConnectionId(event.target.value)
                        }
                        fullWidth
                        size="small"
                      />
                      <TextField
                        label="Account Name"
                        value={accountName}
                        onChange={(event) => setAccountName(event.target.value)}
                        fullWidth
                        size="small"
                      />
                      <TextField
                        label="OAuth Client ID"
                        value={clientId}
                        onChange={(event) => setClientId(event.target.value)}
                        fullWidth
                        size="small"
                      />
                      <TextField
                        label="OAuth Client Secret"
                        value={clientSecret}
                        onChange={(event) =>
                          setClientSecret(event.target.value)
                        }
                        fullWidth
                        size="small"
                        type="password"
                      />
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Button
                          component="label"
                          variant="outlined"
                          startIcon={<CloudUploadOutlinedIcon />}
                        >
                          Upload Client JSON
                          <input
                            hidden
                            type="file"
                            accept=".json,application/json"
                            onChange={handleClientJsonFile}
                          />
                        </Button>
                        {clientJsonFileName && (
                          <Typography color="text.secondary" variant="body2">
                            {clientJsonFileName}
                          </Typography>
                        )}
                      </Stack>
                      <Stack direction="row" spacing={1} alignItems="center">
                        <Button
                          component="label"
                          variant="outlined"
                          startIcon={<CloudUploadOutlinedIcon />}
                        >
                          Upload OAuth Token JSON
                          <input
                            hidden
                            type="file"
                            accept=".json,application/json"
                            onChange={handleTokenJsonFile}
                          />
                        </Button>
                        {tokenJsonFileName && (
                          <Typography color="text.secondary" variant="body2">
                            {tokenJsonFileName}
                          </Typography>
                        )}
                      </Stack>
                      <TextField
                        label="OAuth Token JSON"
                        value={oauthTokenJson}
                        onChange={(event) =>
                          setOauthTokenJson(event.target.value)
                        }
                        fullWidth
                        multiline
                        minRows={10}
                      />
                      <TextField
                        label="Max Files"
                        value={maxFiles}
                        onChange={(event) =>
                          setMaxFiles(
                            Math.max(1, Number(event.target.value) || 1),
                          )
                        }
                        fullWidth
                        size="small"
                        type="number"
                      />
                      <Button
                        variant="outlined"
                        onClick={handleSaveConnection}
                        disabled={saveGDriveConnectionAction.isLoading}
                      >
                        Create Connection
                      </Button>
                      <Button
                        variant="contained"
                        onClick={handleLoad}
                        disabled={loadGDriveAction.isLoading}
                        startIcon={<CloudSyncOutlinedIcon />}
                      >
                        Load Drive
                      </Button>
                      <Button
                        color="error"
                        variant="outlined"
                        onClick={() => handleDeleteConnection(connectionId)}
                        disabled={
                          deleteGDriveConnectionAction.isLoading ||
                          !connectionId.trim()
                        }
                      >
                        Delete Connection
                      </Button>
                    </Stack>
                  )}
                  {activeTab === "manage" && (
                    <Stack spacing={2}>
                      <Stack
                        direction="row"
                        alignItems="center"
                        justifyContent="space-between"
                      >
                        <Typography variant="h6">Connections</Typography>
                        <Button
                          size="small"
                          variant="outlined"
                          onClick={() => refetchConnections()}
                          disabled={isFetchingConnections}
                        >
                          Refresh
                        </Button>
                      </Stack>
                      <TableContainer component={Box}>
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell>Connection ID</TableCell>
                              <TableCell>Account Name</TableCell>
                              <TableCell align="right">Delete</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {connections.length === 0 && (
                              <TableRow>
                                <TableCell colSpan={3}>
                                  No connections saved.
                                </TableCell>
                              </TableRow>
                            )}
                            {connections.map((connection) => (
                              <TableRow key={connection.connectionId}>
                                <TableCell>{connection.connectionId}</TableCell>
                                <TableCell>
                                  {connection.accountName ||
                                    connection.connectionId}
                                </TableCell>
                                <TableCell align="right">
                                  <Stack
                                    direction="row"
                                    justifyContent="flex-end"
                                    spacing={1}
                                  >
                                    <Button
                                      size="small"
                                      onClick={() =>
                                        handleUseConnection(connection)
                                      }
                                    >
                                      Use
                                    </Button>
                                    <Button
                                      color="error"
                                      size="small"
                                      onClick={() =>
                                        handleDeleteConnection(
                                          connection.connectionId,
                                        )
                                      }
                                      disabled={
                                        deleteGDriveConnectionAction.isLoading
                                      }
                                    >
                                      Delete
                                    </Button>
                                  </Stack>
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    </Stack>
                  )}
                  {message && (
                    <Alert severity="success" sx={{ mt: 3 }}>
                      {message}
                    </Alert>
                  )}
                  {error && (
                    <Alert severity="error" sx={{ mt: 3 }}>
                      {error}
                    </Alert>
                  )}
                </Box>
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
