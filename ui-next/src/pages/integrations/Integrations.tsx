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

type ProviderTab = "gdrive" | "gemini" | "reconciliation";
type ConnectionTab = "create" | "manage";

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

type GDriveOAuthClientConfig = {
  configured: boolean;
  clientId?: string;
};

type GeminiConnectionResponse = {
  connectionId: string;
  model?: string;
  configured: boolean;
};

const DEFAULT_MAX_FILES = 25;
const DEFAULT_GEMINI_CONNECTION_ID = "gemini-default";
const DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
const DEFAULT_GEMINI_PROMPT_NAME = "enterj2 name";
const DEFAULT_GEMINI_PROMPT = "enter your prompt";

const createConnectionId = (prefix: "gdrive" | "gemini") => {
  if (window.crypto?.randomUUID) {
    return `${prefix}-${window.crypto.randomUUID()}`;
  }
  return `${prefix}-${Date.now().toString(36)}-${Math.random()
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

const workflowInput = (name: string) => `\${workflow.input.${name}}`;

const buildReadGDriveWorkflowTask = (maxFiles: number) => ({
  name: "read_g_drive",
  taskReferenceName: "read_g_drive_ref",
  type: "GDRIVE_READ",
  inputParameters: {
    connectionId: workflowInput("gdriveConnectionId"),
    folderIds: workflowInput("driveFolderIds"),
    fileIds: workflowInput("driveFileIds"),
    maxFiles,
  },
});

const buildGeminiTask = ({
  connectionId,
  promptname,
  model,
  files,
  prompt,
  gdriveConnectionId,
  taskReferenceName = "gemini_llm_ref",
}: {
  connectionId: string;
  promptname?: string;
  model: string;
  files?: unknown;
  prompt?: string;
  gdriveConnectionId?: string;
  taskReferenceName?: string;
}) => {
  const inputParameters: Record<string, unknown> = {
    connectionId,
    model,
    jsonOutput: true,
  };
  if (promptname !== undefined) {
    inputParameters.promptname = promptname;
  }
  if (prompt !== undefined) {
    inputParameters.prompt = prompt;
  }
  if (files !== undefined) {
    inputParameters.files = files;
  }
  if (gdriveConnectionId !== undefined) {
    inputParameters.gdriveConnectionId = gdriveConnectionId;
  }

  return {
    name: "gemini_llm",
    taskReferenceName,
    type: "GEMINI_LLM",
    inputParameters,
  };
};

const buildGeminiTaskSnippet = ({
  connectionId,
  model,
  promptname,
  prompt,
}: {
  connectionId: string;
  model: string;
  promptname: string;
  prompt: string;
}) => {
  return JSON.stringify(
    buildGeminiTask({
      connectionId,
      model,
      promptname,
      prompt,
      files: "${gdrive_task_ref.output.files}",
    }),
    null,
    2,
  );
};

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
  const [activeProvider, setActiveProvider] = useState<ProviderTab>("gdrive");
  const [gdriveTab, setGdriveTab] = useState<ConnectionTab>("create");
  const [geminiTab, setGeminiTab] = useState<ConnectionTab>("create");

  const [gdriveConnectionId, setGdriveConnectionId] = useState(() =>
    createConnectionId("gdrive"),
  );
  const [gdriveAccountName, setGdriveAccountName] = useState("");
  const [oauthTokenJson, setOauthTokenJson] = useState("");
  const [tokenJsonFileName, setTokenJsonFileName] = useState("");
  const [maxFiles, setMaxFiles] = useState(DEFAULT_MAX_FILES);
  const [result, setResult] = useState<GDriveLoadResponse | null>(null);

  const [geminiConnectionId, setGeminiConnectionId] = useState(
    DEFAULT_GEMINI_CONNECTION_ID,
  );
  const [geminiApiKey, setGeminiApiKey] = useState("");
  const [geminiModel, setGeminiModel] = useState(DEFAULT_GEMINI_MODEL);
  const [geminiPromptName, setGeminiPromptName] = useState(
    DEFAULT_GEMINI_PROMPT_NAME,
  );
  const [geminiPrompt, setGeminiPrompt] = useState(DEFAULT_GEMINI_PROMPT);

  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const {
    data: gdriveConnections = [],
    refetch: refetchGDriveConnections,
    isFetching: isFetchingGDriveConnections,
  } = useFetch<GDriveConnectionResponse[]>("/integrations/gdrive/connections");

  const { data: gdriveOAuthClientConfig } = useFetch<GDriveOAuthClientConfig>(
    "/integrations/gdrive/oauth/client",
    { when: activeProvider === "gdrive" && gdriveTab === "create" },
  );

  const {
    data: geminiConnections = [],
    refetch: refetchGeminiConnections,
    isFetching: isFetchingGeminiConnections,
  } = useFetch<GeminiConnectionResponse[]>("/integrations/gemini/connections");

  const taskSnippet = useMemo(() => {
    if (activeProvider === "gemini") {
      return buildGeminiTaskSnippet({
        connectionId: geminiConnectionId.trim() || DEFAULT_GEMINI_CONNECTION_ID,
        model: geminiModel,
        promptname: geminiPromptName,
        prompt: geminiPrompt,
      });
    }

    if (activeProvider === "reconciliation") {
      return JSON.stringify(
        {
          name: "grn_pod_reconcile",
          taskReferenceName: "grn_pod_reconcile_ref",
          type: "GRN_POD_RECONCILE",
          inputParameters: {
            grnList: workflowInput("grnList"),
            podList: workflowInput("podList"),
          },
        },
        null,
        2,
      );
    }

    return JSON.stringify(buildReadGDriveWorkflowTask(maxFiles), null, 2);
  }, [
    activeProvider,
    geminiConnectionId,
    geminiModel,
    geminiPrompt,
    geminiPromptName,
    maxFiles,
  ]);

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
        setGdriveConnectionId(data.connectionId || gdriveConnectionId);
        setGdriveAccountName(
          data.accountName ||
            gdriveAccountName ||
            data.connectionId ||
            gdriveConnectionId,
        );
        setMessage(
          `Google Drive connection ${data.connectionId || gdriveConnectionId} created.`,
        );
        refetchGDriveConnections();
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
      if (gdriveConnectionId === decodeURIComponent(deletedConnectionId)) {
        setGdriveConnectionId(createConnectionId("gdrive"));
        setGdriveAccountName("");
      }
      refetchGDriveConnections();
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

  const saveGeminiConnectionAction =
    useActionWithPath<GeminiConnectionResponse>({
      onSuccess: (data) => {
        setGeminiConnectionId(data.connectionId || geminiConnectionId);
        setGeminiModel(data.model || geminiModel);
        setGeminiApiKey("");
        setMessage(
          `Gemini connection ${data.connectionId || geminiConnectionId} created.`,
        );
        refetchGeminiConnections();
        setError("");
      },
      onError: async (response: Response) => {
        const message = await getErrorMessage(response);
        setError(
          normalizeErrorMessage(
            message,
            "Unable to save Gemini connection. Enter a Gemini API key or set CONDUCTOR_GEMINI_API_KEY before starting the server.",
            response.status,
          ),
        );
      },
    });

  const deleteGeminiConnectionAction = useActionWithPath({
    onSuccess: (_, variables) => {
      const deletedConnectionId = variables.path.split("/").pop() || "";
      setMessage(
        `Gemini connection ${decodeURIComponent(deletedConnectionId)} deleted.`,
      );
      if (geminiConnectionId === decodeURIComponent(deletedConnectionId)) {
        setGeminiConnectionId(DEFAULT_GEMINI_CONNECTION_ID);
      }
      refetchGeminiConnections();
      setError("");
    },
    onError: async (response: Response) => {
      const message = await getErrorMessage(response);
      setError(
        normalizeErrorMessage(
          message,
          "Unable to delete Gemini connection.",
          response.status,
        ),
      );
    },
  });

  const handleSaveGDriveConnection = () => {
    setError("");
    setMessage("");

    const nextConnectionId =
      gdriveConnectionId.trim() || createConnectionId("gdrive");
    const nextAccountName = gdriveAccountName.trim() || nextConnectionId;
    setGdriveConnectionId(nextConnectionId);
    setGdriveAccountName(nextAccountName);

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
      }),
    });
  };

  const handleLoadGDrive = () => {
    setError("");
    setMessage("");
    setResult(null);

    loadGDriveAction.mutate({
      path: "/integrations/gdrive/load",
      method: "post",
      body: JSON.stringify({
        connectionId: gdriveConnectionId.trim() || undefined,
        maxFiles,
      }),
    });
  };

  const handleDeleteGDriveConnection = (targetConnectionId: string) => {
    deleteGDriveConnectionAction.mutate({
      path: `/integrations/gdrive/connections/${encodeURIComponent(
        targetConnectionId,
      )}`,
      method: "delete",
    });
  };

  const handleUseGDriveConnection = (connection: GDriveConnectionResponse) => {
    setGdriveConnectionId(connection.connectionId);
    setGdriveAccountName(connection.accountName || connection.connectionId);
    setGdriveTab("create");
  };

  const handleSaveGeminiConnection = () => {
    setError("");
    setMessage("");

    const nextConnectionId =
      geminiConnectionId.trim() || DEFAULT_GEMINI_CONNECTION_ID;
    const nextModel = geminiModel.trim() || DEFAULT_GEMINI_MODEL;
    setGeminiConnectionId(nextConnectionId);
    setGeminiModel(nextModel);

    saveGeminiConnectionAction.mutate({
      path: "/integrations/gemini/connections",
      method: "post",
      body: JSON.stringify({
        connectionId: nextConnectionId,
        apiKey: geminiApiKey.trim() || undefined,
        model: nextModel,
      }),
    });
  };

  const handleDeleteGeminiConnection = (targetConnectionId: string) => {
    deleteGeminiConnectionAction.mutate({
      path: `/integrations/gemini/connections/${encodeURIComponent(
        targetConnectionId,
      )}`,
      method: "delete",
    });
  };

  const handleUseGeminiConnection = (connection: GeminiConnectionResponse) => {
    setGeminiConnectionId(connection.connectionId);
    setGeminiModel(connection.model || geminiModel);
    setGeminiTab("create");
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

  const isLoading =
    loadGDriveAction.isLoading ||
    saveGDriveConnectionAction.isLoading ||
    deleteGDriveConnectionAction.isLoading ||
    saveGeminiConnectionAction.isLoading ||
    deleteGeminiConnectionAction.isLoading;

  return (
    <>
      <Helmet>
        <title>Integrations</title>
      </Helmet>
      <Header loading={isLoading} />
      <SectionContainer header={<SectionHeader title="Integrations" />}>
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <Paper variant="outlined" sx={{ p: 3 }}>
              <Stack direction="row" spacing={3} alignItems="stretch">
                <Tabs
                  orientation="vertical"
                  value={activeProvider}
                  onChange={(_, value: ProviderTab) => {
                    setActiveProvider(value);
                    setError("");
                    setMessage("");
                  }}
                  sx={{ borderRight: 1, borderColor: "divider", minWidth: 142 }}
                >
                  <Tab value="gdrive" label="GDrive" />
                  <Tab value="gemini" label="Gemini" />
                  <Tab value="reconciliation" label="Reconciliation" />
                </Tabs>

                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Stack spacing={3}>
                    {activeProvider === "gdrive" && (
                      <Stack direction="row" spacing={3} alignItems="stretch">
                        <Tabs
                          orientation="vertical"
                          value={gdriveTab}
                          onChange={(_, value) => setGdriveTab(value)}
                          sx={{
                            borderRight: 1,
                            borderColor: "divider",
                            minWidth: 120,
                          }}
                        >
                          <Tab value="create" label="Create" />
                          <Tab value="manage" label="Manage" />
                        </Tabs>
                        <Box sx={{ flex: 1, minWidth: 0 }}>
                          {gdriveTab === "create" && (
                            <Stack spacing={3}>
                              <Stack
                                direction="row"
                                alignItems="center"
                                spacing={1}
                              >
                                <Typography variant="h6">
                                  Google Drive
                                </Typography>
                                <Chip label="GDRIVE_READ" size="small" />
                              </Stack>
                              <Alert severity="info">
                                OAuth client ID and secret are loaded from
                                server environment variables. Configure
                                CONDUCTOR_GDRIVE_OAUTH_CLIENT_ID and
                                CONDUCTOR_GDRIVE_OAUTH_CLIENT_SECRET before
                                starting the server.
                              </Alert>
                              {gdriveOAuthClientConfig && (
                                <Chip
                                  color={
                                    gdriveOAuthClientConfig.configured
                                      ? "success"
                                      : "warning"
                                  }
                                  label={
                                    gdriveOAuthClientConfig.configured
                                      ? `OAuth client configured: ${gdriveOAuthClientConfig.clientId || "configured"}`
                                      : "OAuth client env not configured"
                                  }
                                  sx={{ alignSelf: "flex-start" }}
                                />
                              )}
                              <TextField
                                label="Connection ID"
                                value={gdriveConnectionId}
                                onChange={(event) =>
                                  setGdriveConnectionId(event.target.value)
                                }
                                fullWidth
                                size="small"
                              />
                              <TextField
                                label="Account Name"
                                value={gdriveAccountName}
                                onChange={(event) =>
                                  setGdriveAccountName(event.target.value)
                                }
                                fullWidth
                                size="small"
                              />
                              <Stack
                                direction="row"
                                spacing={1}
                                alignItems="center"
                              >
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
                                  <Typography
                                    color="text.secondary"
                                    variant="body2"
                                  >
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
                                    Math.max(
                                      1,
                                      Number(event.target.value) || 1,
                                    ),
                                  )
                                }
                                fullWidth
                                size="small"
                                type="number"
                              />
                              <Button
                                variant="outlined"
                                onClick={handleSaveGDriveConnection}
                                disabled={saveGDriveConnectionAction.isLoading}
                              >
                                Create Connection
                              </Button>
                              <Button
                                variant="contained"
                                onClick={handleLoadGDrive}
                                disabled={loadGDriveAction.isLoading}
                                startIcon={<CloudSyncOutlinedIcon />}
                              >
                                Load Drive
                              </Button>
                            </Stack>
                          )}
                          {gdriveTab === "manage" && (
                            <ConnectionTable
                              title="Google Drive Connections"
                              rows={gdriveConnections.map((connection) => ({
                                connectionId: connection.connectionId,
                                detail:
                                  connection.accountName ||
                                  connection.connectionId,
                              }))}
                              detailHeader="Account Name"
                              emptyText="No Google Drive connections saved."
                              isFetching={isFetchingGDriveConnections}
                              onRefresh={refetchGDriveConnections}
                              onUse={(connectionId) => {
                                const connection = gdriveConnections.find(
                                  (item) => item.connectionId === connectionId,
                                );
                                if (connection) {
                                  handleUseGDriveConnection(connection);
                                }
                              }}
                              onDelete={handleDeleteGDriveConnection}
                              isDeleting={
                                deleteGDriveConnectionAction.isLoading
                              }
                            />
                          )}
                        </Box>
                      </Stack>
                    )}

                    {activeProvider === "gemini" && (
                      <Stack direction="row" spacing={3} alignItems="stretch">
                        <Tabs
                          orientation="vertical"
                          value={geminiTab}
                          onChange={(_, value) => setGeminiTab(value)}
                          sx={{
                            borderRight: 1,
                            borderColor: "divider",
                            minWidth: 120,
                          }}
                        >
                          <Tab value="create" label="Create" />
                          <Tab value="manage" label="Manage" />
                        </Tabs>
                        <Box sx={{ flex: 1, minWidth: 0 }}>
                          {geminiTab === "create" && (
                            <Stack spacing={3}>
                              <Stack
                                direction="row"
                                alignItems="center"
                                spacing={1}
                              >
                                <Typography variant="h6">Gemini</Typography>
                                <Chip label="GEMINI_LLM" size="small" />
                              </Stack>
                              <Alert severity="info">
                                Gemini API key can be entered here or loaded
                                from server environment variables. The workflow
                                task references this connection ID to resolve
                                the API key.
                              </Alert>
                              <TextField
                                label="Connection ID"
                                value={geminiConnectionId}
                                onChange={(event) =>
                                  setGeminiConnectionId(event.target.value)
                                }
                                fullWidth
                                size="small"
                              />
                              <TextField
                                label="Gemini API Key"
                                value={geminiApiKey}
                                onChange={(event) =>
                                  setGeminiApiKey(event.target.value)
                                }
                                fullWidth
                                size="small"
                                type="password"
                              />
                              <TextField
                                label="Model"
                                value={geminiModel}
                                onChange={(event) =>
                                  setGeminiModel(event.target.value)
                                }
                                fullWidth
                                size="small"
                              />
                              <TextField
                                label="Prompt Name"
                                value={geminiPromptName}
                                onChange={(event) =>
                                  setGeminiPromptName(event.target.value)
                                }
                                fullWidth
                                size="small"
                              />
                              <TextField
                                label="Prompt"
                                value={geminiPrompt}
                                onChange={(event) =>
                                  setGeminiPrompt(event.target.value)
                                }
                                fullWidth
                                multiline
                                minRows={4}
                                size="small"
                              />
                              <Button
                                variant="outlined"
                                onClick={handleSaveGeminiConnection}
                                disabled={saveGeminiConnectionAction.isLoading}
                              >
                                Create Connection
                              </Button>
                            </Stack>
                          )}
                          {geminiTab === "manage" && (
                            <ConnectionTable
                              title="Gemini Connections"
                              rows={geminiConnections.map((connection) => ({
                                connectionId: connection.connectionId,
                                detail:
                                  connection.model || DEFAULT_GEMINI_MODEL,
                                status: connection.configured
                                  ? "Configured"
                                  : "Missing",
                              }))}
                              detailHeader="Model"
                              emptyText="No Gemini connections saved."
                              isFetching={isFetchingGeminiConnections}
                              onRefresh={refetchGeminiConnections}
                              onUse={(connectionId) => {
                                const connection = geminiConnections.find(
                                  (item) => item.connectionId === connectionId,
                                );
                                if (connection) {
                                  handleUseGeminiConnection(connection);
                                }
                              }}
                              onDelete={handleDeleteGeminiConnection}
                              isDeleting={
                                deleteGeminiConnectionAction.isLoading
                              }
                            />
                          )}
                        </Box>
                      </Stack>
                    )}

                    {activeProvider === "reconciliation" && (
                      <Stack spacing={3}>
                        <Stack direction="row" alignItems="center" spacing={1}>
                          <Typography variant="h6">
                            GRN/POD Reconciliation
                          </Typography>
                          <Chip label="GRN_POD_RECONCILE" size="small" />
                        </Stack>
                        <Typography color="text.secondary">
                          Reconciliation consumes GRN and POD lists and returns
                          matched results for the workflow.
                        </Typography>
                      </Stack>
                    )}

                    {message && <Alert severity="success">{message}</Alert>}
                    {error && <Alert severity="error">{error}</Alert>}
                  </Stack>
                </Box>
              </Stack>
            </Paper>
          </Grid>

          <Grid size={{ xs: 12, lg: 7 }}>
            <Stack spacing={3}>
              <Paper variant="outlined" sx={{ p: 3 }}>
                <Stack spacing={2}>
                  <Typography variant="h6">Task JSON</Typography>
                  <CodeSnippet code={taskSnippet} className="json" />
                </Stack>
              </Paper>

              {activeProvider === "gdrive" && (
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
                                <TableCell>
                                  {file.modifiedTime || "-"}
                                </TableCell>
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
              )}

              {activeProvider === "reconciliation" && (
                <Paper variant="outlined" sx={{ p: 3 }}>
                  <Stack spacing={2}>
                    <Typography variant="h6">Input Contract</Typography>
                    <Typography color="text.secondary">
                      Pass extracted GRN records into `inputParameters.grnList`
                      and extracted POD records into `inputParameters.podList`.
                      The task returns matched documents and reconciliation
                      rows.
                    </Typography>
                  </Stack>
                </Paper>
              )}
            </Stack>
          </Grid>
        </Grid>
      </SectionContainer>
    </>
  );
}

type ConnectionRow = {
  connectionId: string;
  detail: string;
  status?: string;
};

type ConnectionTableProps = {
  title: string;
  rows: ConnectionRow[];
  detailHeader: string;
  emptyText: string;
  isFetching: boolean;
  isDeleting: boolean;
  onRefresh: () => void;
  onUse: (connectionId: string) => void;
  onDelete: (connectionId: string) => void;
};

const ConnectionTable = ({
  title,
  rows,
  detailHeader,
  emptyText,
  isFetching,
  isDeleting,
  onRefresh,
  onUse,
  onDelete,
}: ConnectionTableProps) => (
  <Stack spacing={2}>
    <Stack direction="row" alignItems="center" justifyContent="space-between">
      <Typography variant="h6">{title}</Typography>
      <Button
        size="small"
        variant="outlined"
        onClick={onRefresh}
        disabled={isFetching}
      >
        Refresh
      </Button>
    </Stack>
    <TableContainer component={Box}>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Connection ID</TableCell>
            <TableCell>{detailHeader}</TableCell>
            <TableCell>Status</TableCell>
            <TableCell align="right">Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={4}>{emptyText}</TableCell>
            </TableRow>
          )}
          {rows.map((connection) => (
            <TableRow key={connection.connectionId}>
              <TableCell>{connection.connectionId}</TableCell>
              <TableCell>{connection.detail}</TableCell>
              <TableCell>{connection.status || "Saved"}</TableCell>
              <TableCell align="right">
                <Stack direction="row" justifyContent="flex-end" spacing={1}>
                  <Button
                    size="small"
                    onClick={() => onUse(connection.connectionId)}
                  >
                    Use
                  </Button>
                  <Button
                    color="error"
                    size="small"
                    onClick={() => onDelete(connection.connectionId)}
                    disabled={isDeleting}
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
);
