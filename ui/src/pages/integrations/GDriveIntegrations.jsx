import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { Helmet } from "react-helmet";
import { makeStyles } from "@material-ui/styles";
import {
  Box,
  Button,
  Chip,
  Grid,
  Link,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
} from "@material-ui/core";
import CloudQueueIcon from "@material-ui/icons/CloudQueue";
import FolderOpenIcon from "@material-ui/icons/FolderOpen";
import Input from "../../components/Input";
import Paper from "../../components/Paper";
import Text from "../../components/Text";
import { fetchWithContext, useFetchContext } from "../../plugins/fetch";

const DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly";
const SESSION_KEY = "conductor.gdrive.oauth";
const READ_G_DRIVE_TASK_NAME = "read_g_drive";
const GEMINI_TASK_NAME = "gemini_llm";
const GEMINI_RECONCILE_TASK_NAME = "grn_pod_reconcile";
const DEFAULT_GEMINI_CONNECTION_ID = "gemini-default";
const DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
const DEFAULT_GEMINI_PROMPT_NAME = "enterj2 name";
const DEFAULT_GEMINI_PROMPT = "enter your prompt";

function createConnectionId() {
  if (window.crypto && window.crypto.randomUUID) {
    return `gdrive-${window.crypto.randomUUID()}`;
  }
  return `gdrive-${Date.now().toString(36)}-${Math.random()
    .toString(36)
    .slice(2, 10)}`;
}

const useStyles = makeStyles({
  root: {
    height: "100%",
    overflowY: "auto",
    overflowX: "hidden",
    padding: 24,
    boxSizing: "border-box",
  },
  title: {
    fontSize: 26,
    fontWeight: 700,
    margin: "20px 0 24px",
  },
  card: {
    height: "100%",
    border: "1px solid #e2e2e2",
  },
  selector: {
    border: "1px solid #1976d2",
    borderRadius: 4,
    padding: 18,
    display: "flex",
    gap: 14,
    alignItems: "center",
    backgroundColor: "#f8fbff",
  },
  selectorIcon: {
    color: "#1976d2",
  },
  formStack: {
    display: "flex",
    flexDirection: "column",
    gap: 18,
  },
  fileInput: {
    display: "none",
  },
  codeBlock: {
    margin: 0,
    padding: 16,
    borderRadius: 4,
    background: "#111827",
    color: "#f9fafb",
    overflow: "auto",
    fontSize: 12,
    lineHeight: 1.5,
  },
  actionRow: {
    display: "flex",
    gap: 12,
    flexWrap: "wrap",
    alignItems: "center",
  },
  muted: {
    color: "#666",
  },
  tabbedPanel: {
    display: "flex",
    gap: 20,
    alignItems: "stretch",
  },
  verticalTabs: {
    minWidth: 118,
    borderRight: "1px solid #e2e2e2",
  },
  tabContent: {
    flex: 1,
    minWidth: 0,
  },
  connectionList: {
    border: "1px solid #e2e2e2",
    borderRadius: 4,
    overflow: "hidden",
  },
  connectionRow: {
    display: "grid",
    gridTemplateColumns: "minmax(0, 1fr) minmax(0, 1fr) auto",
    gap: 12,
    alignItems: "center",
    padding: "10px 12px",
    borderBottom: "1px solid #eeeeee",
  },
  connectionHeader: {
    fontWeight: 700,
    backgroundColor: "#fafafa",
  },
  truncate: {
    overflow: "hidden",
    textOverflow: "ellipsis",
    whiteSpace: "nowrap",
  },
  table: {
    minWidth: 620,
  },
});

function safeParseJson(value) {
  if (!value || !value.trim()) {
    return null;
  }
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function buildOAuthUrl({ connectionId, clientId }) {
  if (!clientId) {
    return "";
  }

  const redirectUri =
    window.location.origin + window.location.pathname.replace(/\/+$/, "");

  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: "code",
    scope: DRIVE_SCOPE,
    access_type: "offline",
    prompt: "consent",
    include_granted_scopes: "true",
    state: connectionId || "",
  });

  return `https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`;
}

function downloadJsonFile(fileName, content) {
  if (!content) {
    return;
  }
  const blob = new Blob([content], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function workflowInput(name) {
  return `${String.fromCharCode(36)}{workflow.input.${name}}`;
}

function taskSnippet(maxFiles) {
  return JSON.stringify(buildReadDriveWorkflowTask(maxFiles), null, 2);
}

function reconcileTaskSnippet() {
  return JSON.stringify(
    {
      name: GEMINI_RECONCILE_TASK_NAME,
      taskReferenceName: `${GEMINI_RECONCILE_TASK_NAME}_ref`,
      type: "GRN_POD_RECONCILE",
      inputParameters: {
        grnList: workflowInput("grnList"),
        podList: workflowInput("podList"),
      },
    },
    null,
    2
  );
}

function buildReadDriveWorkflowTask(maxFiles) {
  return {
    name: READ_G_DRIVE_TASK_NAME,
    taskReferenceName: `${READ_G_DRIVE_TASK_NAME}_ref`,
    type: "GDRIVE_READ",
    inputParameters: {
      connectionId: workflowInput("gdriveConnectionId"),
      folderIds: workflowInput("driveFolderIds"),
      fileIds: workflowInput("driveFileIds"),
      maxFiles,
    },
  };
}

function buildGeminiTask({
  connectionId,
  promptname,
  model,
  files,
  prompt,
  gdriveConnectionId,
  taskReferenceName = `${GEMINI_TASK_NAME}_ref`,
}) {
  const inputParameters = {
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
    name: GEMINI_TASK_NAME,
    taskReferenceName,
    type: "GEMINI_LLM",
    inputParameters,
  };
}

function geminiTaskSnippet({ connectionId, model, promptname, prompt }) {
  return JSON.stringify(
    buildGeminiTask({
      connectionId,
      model,
      promptname,
      prompt,
      files: `${String.fromCharCode(36)}{gdrive_task_ref.output.files}`,
    }),
    null,
    2
  );
}

function formatError(error) {
  if (!error) {
    return "";
  }
  if (typeof error === "string") {
    return normalizeErrorMessage(error);
  }
  return normalizeErrorMessage(error.message || "Request failed");
}

function normalizeErrorMessage(message) {
  const value = String(message || "").trim();
  if (!value) {
    return "Request failed";
  }

  if (/<html[\s>]/i.test(value) || /<body[\s>]/i.test(value)) {
    const heading = value.match(/<h1[^>]*>(.*?)<\/h1>/i);
    const title = value.match(/<title[^>]*>(.*?)<\/title>/i);
    const label = (heading && heading[1]) || (title && title[1]);
    if (label) {
      return `Request failed: ${label.replace(/\s+/g, " ").trim()}.`;
    }
    return "Request failed. The server returned an HTML error page.";
  }

  return value;
}

function buildReadGDriveTaskDefinition() {
  return {
    name: READ_G_DRIVE_TASK_NAME,
    description:
      "Read file metadata from Google Drive using a stored account connection.",
    retryCount: 3,
    timeoutSeconds: 3600,
    inputKeys: [
      "connectionId",
      "folderIds",
      "fileIds",
      "maxFiles",
      "mimeTypes",
    ],
    outputKeys: ["folderIds", "fileIds", "files", "documents", "count"],
    timeoutPolicy: "TIME_OUT_WF",
    retryLogic: "FIXED",
    retryDelaySeconds: 60,
    responseTimeoutSeconds: 600,
    rateLimitPerFrequency: 0,
    rateLimitFrequencyInSeconds: 1,
    ownerEmail: "integrations@conductor.local",
    inputTemplate: {
      connectionId: workflowInput("gdriveConnectionId"),
      folderIds: [],
      fileIds: [],
      maxFiles: 100,
      mimeTypes: [],
    },
  };
}

async function ensureReadGDriveTask(fetchContext) {
  try {
    await fetchWithContext(
      `metadata/taskdefs/${READ_G_DRIVE_TASK_NAME}`,
      fetchContext
    );
    return "available";
  } catch {
    await fetchWithContext("metadata/taskdefs", fetchContext, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify([buildReadGDriveTaskDefinition()]),
    });
    return "created";
  }
}

export default function GDriveIntegrations() {
  const classes = useStyles();
  const fetchContext = useFetchContext();
  const requestContext = useMemo(
    () => ({ ready: fetchContext.ready, stack: fetchContext.stack }),
    [fetchContext.ready, fetchContext.stack]
  );
  const [providerTab, setProviderTab] = useState("gdrive");
  const [activeTab, setActiveTab] = useState("create");
  const [geminiTab, setGeminiTab] = useState("create");
  const [connectionId, setConnectionId] = useState(createConnectionId);
  const [accountName, setAccountName] = useState("");
  const [clientId, setClientId] = useState("");
  const [envOAuthClientConfigured, setEnvOAuthClientConfigured] =
    useState(false);
  const [oauthTokenJson, setOauthTokenJson] = useState("");
  const [tokenJsonFileName, setTokenJsonFileName] = useState("");
  const [maxFiles, setMaxFiles] = useState(100);
  const [files, setFiles] = useState([]);
  const [count, setCount] = useState(0);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [connections, setConnections] = useState([]);
  const [geminiConnectionId, setGeminiConnectionId] = useState(
    DEFAULT_GEMINI_CONNECTION_ID
  );
  const [geminiApiKey, setGeminiApiKey] = useState("");
  const [geminiModel, setGeminiModel] = useState(DEFAULT_GEMINI_MODEL);
  const [geminiPromptName, setGeminiPromptName] = useState(
    DEFAULT_GEMINI_PROMPT_NAME
  );
  const [geminiPrompt, setGeminiPrompt] = useState(DEFAULT_GEMINI_PROMPT);
  const [geminiConnections, setGeminiConnections] = useState([]);
  const processedOAuthCode = useRef(false);

  const refreshConnections = useCallback(() => {
    return fetchWithContext("integrations/gdrive/connections", requestContext)
      .then((response) => setConnections(response || []))
      .catch((err) => setError(formatError(err)));
  }, [requestContext]);

  useEffect(() => {
    refreshConnections();
  }, [refreshConnections]);

  useEffect(() => {
    fetchWithContext("integrations/gdrive/oauth/client", requestContext)
      .then((response) => {
        if (response && response.configured) {
          setEnvOAuthClientConfigured(true);
          setClientId(response.clientId || "");
        }
      })
      .catch(() => {});
  }, [requestContext]);

  const refreshGemini = useCallback(() => {
    return fetchWithContext("integrations/gemini/connections", requestContext)
      .then((connectionsResponse) =>
        setGeminiConnections(connectionsResponse || [])
      )
      .catch((err) => setError(formatError(err)));
  }, [requestContext]);

  useEffect(() => {
    refreshGemini();
  }, [refreshGemini]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get("code");
    if (!code || processedOAuthCode.current) {
      return;
    }
    processedOAuthCode.current = true;

    const stored = safeParseJson(sessionStorage.getItem(SESSION_KEY)) || {};
    const storedClientId = stored.clientId || "";
    const storedConnectionId = stored.connectionId || params.get("state") || "";
    const storedAccountName = stored.accountName || storedConnectionId;
    const redirectUri =
      window.location.origin + window.location.pathname.replace(/\/+$/, "");

    setConnectionId(storedConnectionId);
    setAccountName(storedAccountName);
    setClientId(storedClientId);
    setLoading(true);
    setError("");
    setMessage("Completing Google OAuth...");

    fetchWithContext("integrations/gdrive/oauth/token", requestContext, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        connectionId: storedConnectionId,
        accountName: storedAccountName,
        authorizationCode: code,
        redirectUri,
      }),
    })
      .then(async (response) => {
        const savedConnectionId = response.connectionId || storedConnectionId;
        if (!savedConnectionId) {
          throw new Error("OAuth response did not include a connection ID.");
        }
        downloadJsonFile(
          `${savedConnectionId}-token.json`,
          response.oauthTokenJson
        );
        sessionStorage.removeItem(SESSION_KEY);
        window.history.replaceState({}, "", window.location.pathname);

        try {
          const taskStatus = await ensureReadGDriveTask(requestContext);
          const taskMessage =
            taskStatus === "created"
              ? `${READ_G_DRIVE_TASK_NAME} task created.`
              : `${READ_G_DRIVE_TASK_NAME} task is available.`;
          const downloadMessage = response.oauthTokenJson
            ? " token.json downloaded."
            : "";
          setMessage(
            `Google Drive connection ${savedConnectionId} saved.${downloadMessage} ${taskMessage}`
          );
        } catch (taskError) {
          const downloadMessage = response.oauthTokenJson
            ? " token.json downloaded."
            : "";
          setMessage(
            `Google Drive connection ${savedConnectionId} saved.${downloadMessage}`
          );
          setError(
            `${READ_G_DRIVE_TASK_NAME} task was not created: ${formatError(
              taskError
            )}`
          );
        }
        refreshConnections();
      })
      .catch((err) => setError(formatError(err)))
      .finally(() => setLoading(false));
  }, [requestContext, refreshConnections]);

  function handleGenerateOAuth() {
    setError("");
    setMessage("");

    const nextConnectionId = connectionId.trim() || createConnectionId();
    const nextAccountName = accountName.trim() || nextConnectionId;
    setConnectionId(nextConnectionId);
    setAccountName(nextAccountName);
    const nextOAuthUrl = buildOAuthUrl({
      connectionId: nextConnectionId,
      clientId: clientId.trim(),
    });

    if (!nextOAuthUrl) {
      setError(
        "OAuth client ID is required. Configure CONDUCTOR_GDRIVE_OAUTH_CLIENT_ID before starting the server."
      );
      return;
    }

    sessionStorage.setItem(
      SESSION_KEY,
      JSON.stringify({
        connectionId: nextConnectionId,
        accountName: nextAccountName,
        clientId: clientId.trim(),
      })
    );
    window.location.assign(nextOAuthUrl);
  }

  function handleOAuthTokenFileChange(event) {
    const file = event.target.files && event.target.files[0];
    if (!file) {
      return;
    }
    file
      .text()
      .then((content) => {
        const parsed = JSON.parse(content);
        if (!(parsed.access_token || parsed.token || parsed.refresh_token)) {
          throw new Error(
            "OAuth token JSON must include access_token, token, or refresh_token."
          );
        }
        setOauthTokenJson(content);
        setTokenJsonFileName(file.name);
        setMessage(`${file.name} loaded as OAuth token JSON.`);
        setError("");
      })
      .catch((err) =>
        setError(err.message || "OAuth token JSON file is invalid.")
      );
  }

  function handleSaveConnection() {
    setError("");
    setMessage("");

    const nextConnectionId = connectionId.trim() || createConnectionId();
    const nextAccountName = accountName.trim() || nextConnectionId;
    setConnectionId(nextConnectionId);
    setAccountName(nextAccountName);
    if (!safeParseJson(oauthTokenJson)) {
      setError("OAuth token JSON is required before saving the connection.");
      return;
    }

    setLoading(true);
    fetchWithContext("integrations/gdrive/connections", requestContext, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        connectionId: nextConnectionId,
        accountName: nextAccountName,
        oauthTokenJson,
      }),
    })
      .then((response) => {
        const savedConnectionId = response.connectionId || nextConnectionId;
        setConnectionId(savedConnectionId);
        setAccountName(response.accountName || nextAccountName);
        setMessage(`Google Drive connection ${savedConnectionId} created.`);
        refreshConnections();
      })
      .catch((err) => setError(formatError(err)))
      .finally(() => setLoading(false));
  }

  function handleSaveGeminiConnection() {
    setError("");
    setMessage("");
    setLoading(true);
    const nextConnectionId =
      geminiConnectionId.trim() || DEFAULT_GEMINI_CONNECTION_ID;
    fetchWithContext("integrations/gemini/connections", requestContext, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        connectionId: nextConnectionId,
        apiKey: geminiApiKey.trim() || undefined,
        model: geminiModel.trim() || DEFAULT_GEMINI_MODEL,
      }),
    })
      .then((response) => {
        setGeminiConnectionId(response.connectionId || geminiConnectionId);
        setGeminiModel(response.model || geminiModel);
        setGeminiApiKey("");
        setMessage(
          `Gemini connection ${
            response.connectionId || geminiConnectionId
          } saved.`
        );
        return refreshGemini();
      })
      .catch((err) => setError(formatError(err)))
      .finally(() => setLoading(false));
  }

  function handleDeleteGeminiConnection(targetConnectionId) {
    setError("");
    setMessage("");
    setLoading(true);
    fetchWithContext(
      `integrations/gemini/connections/${encodeURIComponent(
        targetConnectionId
      )}`,
      requestContext,
      { method: "DELETE" },
      false
    )
      .then(() => {
        setMessage(`Gemini connection ${targetConnectionId} deleted.`);
        if (geminiConnectionId === targetConnectionId) {
          setGeminiConnectionId(DEFAULT_GEMINI_CONNECTION_ID);
        }
        return refreshGemini();
      })
      .catch((err) => setError(formatError(err)))
      .finally(() => setLoading(false));
  }

  function handleLoadDrive() {
    setError("");
    setMessage("");
    setFiles([]);
    setCount(0);

    setLoading(true);
    fetchWithContext("integrations/gdrive/load", requestContext, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        connectionId: connectionId.trim() || undefined,
        maxFiles,
      }),
    })
      .then((response) => {
        setFiles(response.files || []);
        setCount(response.count || 0);
        setMessage("Google Drive data loaded.");
      })
      .catch((err) => setError(formatError(err)))
      .finally(() => setLoading(false));
  }

  function handleDeleteConnection(targetConnectionId) {
    setError("");
    setMessage("");
    setLoading(true);
    fetchWithContext(
      `integrations/gdrive/connections/${encodeURIComponent(
        targetConnectionId
      )}`,
      requestContext,
      { method: "DELETE" },
      false
    )
      .then(() => {
        setMessage(`Google Drive connection ${targetConnectionId} deleted.`);
        if (connectionId === targetConnectionId) {
          setConnectionId(createConnectionId());
          setAccountName("");
        }
        return refreshConnections();
      })
      .catch((err) => setError(formatError(err)))
      .finally(() => setLoading(false));
  }

  function handleUseConnection(connection) {
    setConnectionId(connection.connectionId || "");
    setAccountName(connection.accountName || connection.connectionId || "");
    setActiveTab("create");
  }

  function handleUseGeminiConnection(connection) {
    setGeminiConnectionId(
      connection.connectionId || DEFAULT_GEMINI_CONNECTION_ID
    );
    setGeminiModel(connection.model || geminiModel);
    setGeminiTab("create");
  }

  return (
    <>
      <Helmet>
        <title>Conductor UI - Integrations</title>
      </Helmet>
      <div className={classes.root}>
        <h1 className={classes.title}>Integrations</h1>
        <Grid container spacing={3}>
          <Grid item xs={12} md={3}>
            <Paper padded className={classes.card}>
              <div className={classes.tabbedPanel}>
                <Tabs
                  orientation="vertical"
                  value={providerTab}
                  onChange={(event, value) => {
                    setProviderTab(value);
                    setError("");
                    setMessage("");
                  }}
                  className={classes.verticalTabs}
                  indicatorColor="primary"
                  textColor="primary"
                >
                  <Tab value="gdrive" label="GDrive" />
                  <Tab value="gemini" label="Gemini" />
                  <Tab value="reconciliation" label="Reconciliation" />
                </Tabs>
                <div className={classes.tabContent}>
                  <div className={classes.selector}>
                    <CloudQueueIcon className={classes.selectorIcon} />
                    <Box>
                      {providerTab === "gdrive" && (
                        <>
                          <Text level={2}>Google Drive Read</Text>
                          <Text className={classes.muted}>
                            Task: {READ_G_DRIVE_TASK_NAME}
                          </Text>
                          <Text className={classes.muted}>
                            Type: GDRIVE_READ
                          </Text>
                        </>
                      )}
                      {providerTab === "gemini" && (
                        <>
                          <Text level={2}>Gemini LLM</Text>
                          <Text className={classes.muted}>
                            Task: {GEMINI_TASK_NAME}
                          </Text>
                          <Text className={classes.muted}>
                            Type: GEMINI_LLM
                          </Text>
                        </>
                      )}
                      {providerTab === "reconciliation" && (
                        <>
                          <Text level={2}>Reconciliation</Text>
                          <Text className={classes.muted}>
                            Task: {GEMINI_RECONCILE_TASK_NAME}
                          </Text>
                          <Text className={classes.muted}>
                            Type: GRN_POD_RECONCILE
                          </Text>
                        </>
                      )}
                    </Box>
                  </div>
                </div>
              </div>
            </Paper>
          </Grid>

          {providerTab === "gdrive" && (
            <>
              <Grid item xs={12} md={5}>
                <Paper padded className={classes.card}>
                  <div className={classes.tabbedPanel}>
                    <Tabs
                      orientation="vertical"
                      value={activeTab}
                      onChange={(event, value) => setActiveTab(value)}
                      className={classes.verticalTabs}
                    >
                      <Tab value="create" label="Create" />
                      <Tab value="manage" label="Manage" />
                    </Tabs>
                    <div className={classes.tabContent}>
                      {activeTab === "create" && (
                        <div className={classes.formStack}>
                          <Box className={classes.actionRow}>
                            <Text level={2}>Google Drive</Text>
                            <Chip size="small" label="Account connection" />
                          </Box>
                          <Text className={classes.muted}>
                            OAuth client ID and secret are loaded from server
                            environment variables.
                          </Text>
                          <Chip
                            size="small"
                            label={
                              envOAuthClientConfigured
                                ? `OAuth configured: ${
                                    clientId || "server env"
                                  }`
                                : "OAuth env not configured"
                            }
                            color={
                              envOAuthClientConfigured ? "primary" : "default"
                            }
                          />
                          <Input
                            label="Connection ID"
                            value={connectionId}
                            fullWidth
                            variant="outlined"
                            onChange={setConnectionId}
                          />
                          <Input
                            label="Account Name"
                            value={accountName}
                            fullWidth
                            variant="outlined"
                            onChange={setAccountName}
                          />
                          <Box className={classes.actionRow}>
                            <input
                              id="gdrive-oauth-token-json-file"
                              className={classes.fileInput}
                              type="file"
                              accept=".json,application/json"
                              onChange={handleOAuthTokenFileChange}
                            />
                            <label htmlFor="gdrive-oauth-token-json-file">
                              <Button
                                component="span"
                                variant="outlined"
                                startIcon={<FolderOpenIcon />}
                              >
                                Upload OAuth Token JSON
                              </Button>
                            </label>
                            {tokenJsonFileName && (
                              <Text className={classes.muted}>
                                {tokenJsonFileName}
                              </Text>
                            )}
                          </Box>
                          <TextField
                            label="OAuth Token JSON for import"
                            value={oauthTokenJson}
                            onChange={(event) =>
                              setOauthTokenJson(event.target.value)
                            }
                            fullWidth
                            multiline
                            minRows={9}
                            variant="outlined"
                          />
                          <Input
                            label="Max Files"
                            value={maxFiles}
                            fullWidth
                            variant="outlined"
                            type="number"
                            onChange={(value) =>
                              setMaxFiles(Math.max(1, Number(value)))
                            }
                          />
                          <Box className={classes.actionRow}>
                            <Button
                              color="primary"
                              variant="contained"
                              onClick={handleGenerateOAuth}
                              disabled={loading}
                            >
                              Generate OAuth
                            </Button>
                            <Button
                              color="primary"
                              variant="outlined"
                              onClick={handleSaveConnection}
                              disabled={loading}
                            >
                              Create Connection
                            </Button>
                            <Button
                              color="primary"
                              variant="outlined"
                              onClick={handleLoadDrive}
                              disabled={loading}
                            >
                              Load Drive
                            </Button>
                          </Box>
                        </div>
                      )}
                      {activeTab === "manage" && (
                        <div className={classes.formStack}>
                          <Box className={classes.actionRow}>
                            <Text level={2}>Connections</Text>
                            <Button
                              variant="outlined"
                              size="small"
                              onClick={refreshConnections}
                              disabled={loading}
                            >
                              Refresh
                            </Button>
                          </Box>
                          <div className={classes.connectionList}>
                            <div
                              className={`${classes.connectionRow} ${classes.connectionHeader}`}
                            >
                              <div>Connection ID</div>
                              <div>Account Name</div>
                              <div />
                            </div>
                            {connections.length === 0 && (
                              <div className={classes.connectionRow}>
                                <div>No connections saved.</div>
                                <div />
                                <div />
                              </div>
                            )}
                            {connections.map((connection) => (
                              <div
                                key={connection.connectionId}
                                className={classes.connectionRow}
                              >
                                <div className={classes.truncate}>
                                  {connection.connectionId}
                                </div>
                                <div className={classes.truncate}>
                                  {connection.accountName ||
                                    connection.connectionId}
                                </div>
                                <Box className={classes.actionRow}>
                                  <Button
                                    size="small"
                                    onClick={() =>
                                      handleUseConnection(connection)
                                    }
                                  >
                                    Use
                                  </Button>
                                  <Button
                                    size="small"
                                    color="primary"
                                    onClick={() =>
                                      handleDeleteConnection(
                                        connection.connectionId
                                      )
                                    }
                                    disabled={loading}
                                  >
                                    Delete
                                  </Button>
                                </Box>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                      {message && <Text>{message}</Text>}
                      {error && <Text color="error">{error}</Text>}
                    </div>
                  </div>
                </Paper>
              </Grid>
              <Grid item xs={12} md={7}>
                <Paper padded className={classes.card}>
                  <Text level={2}>Workflow Task</Text>
                  <pre className={classes.codeBlock}>
                    {taskSnippet(maxFiles)}
                  </pre>
                </Paper>
              </Grid>
              <Grid item xs={12}>
                <Paper padded>
                  <Box className={classes.actionRow} mb={2}>
                    <Text level={2}>Files</Text>
                    <Chip size="small" label={`${count} loaded`} />
                  </Box>
                  <TableContainer>
                    <Table className={classes.table} size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>Name</TableCell>
                          <TableCell>MIME Type</TableCell>
                          <TableCell>Modified</TableCell>
                          <TableCell>ID</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {files.length === 0 && (
                          <TableRow>
                            <TableCell colSpan={4}>No files loaded.</TableCell>
                          </TableRow>
                        )}
                        {files.map((file) => (
                          <TableRow key={file.id}>
                            <TableCell>
                              {file.webViewLink ? (
                                <Link
                                  href={file.webViewLink}
                                  target="_blank"
                                  rel="noreferrer"
                                >
                                  {file.name}
                                </Link>
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
                </Paper>
              </Grid>
            </>
          )}

          {providerTab === "gemini" && (
            <>
              <Grid item xs={12} md={5}>
                <Paper padded className={classes.card}>
                  <div className={classes.tabbedPanel}>
                    <Tabs
                      orientation="vertical"
                      value={geminiTab}
                      onChange={(event, value) => setGeminiTab(value)}
                      className={classes.verticalTabs}
                    >
                      <Tab value="create" label="Create" />
                      <Tab value="manage" label="Manage" />
                    </Tabs>
                    <div className={classes.tabContent}>
                      {geminiTab === "create" && (
                        <div className={classes.formStack}>
                          <Box className={classes.actionRow}>
                            <Text level={2}>Gemini LLM</Text>
                            <Chip size="small" label="GEMINI_LLM" />
                          </Box>
                          <Text className={classes.muted}>
                            Gemini API key can be entered here or loaded from
                            server environment variables. The workflow task
                            references this connection ID to resolve the API
                            key.
                          </Text>
                          <Input
                            label="Connection ID"
                            value={geminiConnectionId}
                            fullWidth
                            variant="outlined"
                            onChange={setGeminiConnectionId}
                          />
                          <Input
                            label="Gemini API Key"
                            value={geminiApiKey}
                            fullWidth
                            variant="outlined"
                            type="password"
                            onChange={setGeminiApiKey}
                          />
                          <Input
                            label="Model"
                            value={geminiModel}
                            fullWidth
                            variant="outlined"
                            onChange={setGeminiModel}
                          />
                          
                          <Box className={classes.actionRow}>
                            <Button
                              color="primary"
                              variant="contained"
                              onClick={handleSaveGeminiConnection}
                              disabled={loading}
                            >
                              Create Connection
                            </Button>
                            <Button
                              variant="outlined"
                              onClick={refreshGemini}
                              disabled={loading}
                            >
                              Refresh
                            </Button>
                          </Box>
                        </div>
                      )}
                      {geminiTab === "manage" && (
                        <div className={classes.formStack}>
                          <Box className={classes.actionRow}>
                            <Text level={2}>Gemini Connections</Text>
                            <Button
                              variant="outlined"
                              size="small"
                              onClick={refreshGemini}
                              disabled={loading}
                            >
                              Refresh
                            </Button>
                          </Box>
                          <div className={classes.connectionList}>
                            <div
                              className={`${classes.connectionRow} ${classes.connectionHeader}`}
                            >
                              <div>Connection ID</div>
                              <div>Model</div>
                              <div />
                            </div>
                            {geminiConnections.length === 0 && (
                              <div className={classes.connectionRow}>
                                <div>No Gemini connections saved.</div>
                                <div />
                                <div />
                              </div>
                            )}
                            {geminiConnections.map((connection) => (
                              <div
                                key={connection.connectionId}
                                className={classes.connectionRow}
                              >
                                <div className={classes.truncate}>
                                  {connection.connectionId}
                                </div>
                                <div className={classes.truncate}>
                                  {connection.model || DEFAULT_GEMINI_MODEL}
                                </div>
                                <Box className={classes.actionRow}>
                                  <Button
                                    size="small"
                                    onClick={() =>
                                      handleUseGeminiConnection(connection)
                                    }
                                  >
                                    Use
                                  </Button>
                                  <Button
                                    size="small"
                                    color="primary"
                                    onClick={() =>
                                      handleDeleteGeminiConnection(
                                        connection.connectionId
                                      )
                                    }
                                    disabled={loading}
                                  >
                                    Delete
                                  </Button>
                                </Box>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                      {message && <Text>{message}</Text>}
                      {error && <Text color="error">{error}</Text>}
                    </div>
                  </div>
                </Paper>
              </Grid>
              <Grid item xs={12} md={4}>
                <Paper padded className={classes.card}>
                  <Text level={2}>Task JSON</Text>
                  <pre className={classes.codeBlock}>
                    {geminiTaskSnippet({
                      connectionId:
                        geminiConnectionId.trim() ||
                        DEFAULT_GEMINI_CONNECTION_ID,
                      model: geminiModel,
                      promptname: geminiPromptName,
                      prompt: geminiPrompt,
                    })}
                  </pre>
                </Paper>
              </Grid>
            </>
          )}

          {providerTab === "reconciliation" && (
            <Grid item xs={12} md={9}>
              <Paper padded className={classes.card}>
                <Text level={2}>Reconciliation Task</Text>
                <Text className={classes.muted}>
                  Reconciliation consumes GRN and POD lists and returns matched
                  results.
                </Text>
                <pre className={classes.codeBlock}>
                  {reconcileTaskSnippet()}
                </pre>
              </Paper>
            </Grid>
          )}
        </Grid>
      </div>
    </>
  );
}
