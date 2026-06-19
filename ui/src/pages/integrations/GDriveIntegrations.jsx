import React, { useEffect, useRef, useState } from "react";
import { Helmet } from "react-helmet";
import { makeStyles } from "@material-ui/styles";
import {
  Box,
  Button,
  Chip,
  Grid,
  Link,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
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

function getOAuthClient(jsonText) {
  const json = safeParseJson(jsonText);
  if (!json) {
    return {};
  }

  const client = json.installed || json.web || json;
  return {
    clientId: client.client_id || json.client_id,
    clientSecret: client.client_secret || json.client_secret,
    redirectUri:
      window.location.origin +
      window.location.pathname.replace(/\/+$/, "") +
      window.location.search.replace(/\?.*$/, ""),
  };
}

function buildOAuthClientJson(clientId, clientSecret) {
  return JSON.stringify({
    installed: {
      client_id: clientId,
      client_secret: clientSecret,
      auth_uri: "https://accounts.google.com/o/oauth2/auth",
      token_uri: "https://oauth2.googleapis.com/token",
    },
  });
}

function buildOAuthUrl({ folderId, clientId }) {
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
    state: folderId || "",
  });

  return `https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`;
}

function workflowInput(name) {
  return `${String.fromCharCode(36)}{workflow.input.${name}}`;
}

function taskSnippet(maxFiles) {
  return JSON.stringify(
    {
      name: READ_G_DRIVE_TASK_NAME,
      taskReferenceName: `${READ_G_DRIVE_TASK_NAME}_ref`,
      type: "GDRIVE_READ",
      inputParameters: {
        folderId: workflowInput("folderId"),
        oauthTokenJson: workflowInput("oauthTokenJson"),
        maxFiles,
      },
    },
    null,
    2
  );
}

function formatError(error) {
  if (!error) {
    return "";
  }
  if (typeof error === "string") {
    return error;
  }
  return error.message || "Request failed";
}

function buildReadGDriveTaskDefinition() {
  return {
    name: READ_G_DRIVE_TASK_NAME,
    description: "Read file metadata from a Google Drive folder using OAuth.",
    retryCount: 3,
    timeoutSeconds: 3600,
    inputKeys: ["folderId", "oauthTokenJson", "maxFiles", "mimeTypes"],
    outputKeys: ["folderId", "files", "count"],
    timeoutPolicy: "TIME_OUT_WF",
    retryLogic: "FIXED",
    retryDelaySeconds: 60,
    responseTimeoutSeconds: 600,
    rateLimitPerFrequency: 0,
    rateLimitFrequencyInSeconds: 1,
    ownerEmail: "integrations@conductor.local",
    inputTemplate: {
      folderId: "",
      oauthTokenJson: "",
      maxFiles: 100,
      mimeTypes: [],
    },
  };
}

function downloadTokenJson(oauthTokenJson) {
  const blob = new Blob([oauthTokenJson], { type: "application/json" });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "token.json";
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => window.URL.revokeObjectURL(url), 0);
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
  const [folderId, setFolderId] = useState("");
  const [clientId, setClientId] = useState("");
  const [clientSecret, setClientSecret] = useState("");
  const [oauthTokenJson, setOauthTokenJson] = useState("");
  const [clientJsonFileName, setClientJsonFileName] = useState("");
  const [tokenJsonFileName, setTokenJsonFileName] = useState("");
  const [maxFiles, setMaxFiles] = useState(100);
  const [files, setFiles] = useState([]);
  const [count, setCount] = useState(0);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const processedOAuthCode = useRef(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get("code");
    if (!code || processedOAuthCode.current) {
      return;
    }
    processedOAuthCode.current = true;

    const stored = safeParseJson(sessionStorage.getItem(SESSION_KEY)) || {};
    const storedClientId = stored.clientId || "";
    const storedClientSecret = stored.clientSecret || "";
    const storedClientJson =
      stored.oauthClientJson ||
      buildOAuthClientJson(storedClientId, storedClientSecret);
    const storedFolderId = stored.folderId || params.get("state") || "";
    const redirectUri =
      window.location.origin + window.location.pathname.replace(/\/+$/, "");

    setFolderId(storedFolderId);
    setClientId(storedClientId);
    setClientSecret(storedClientSecret);
    setLoading(true);
    setError("");
    setMessage("Completing Google OAuth...");

    fetchWithContext("integrations/gdrive/oauth/token", fetchContext, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        authorizationCode: code,
        oauthClientJson: storedClientJson,
        redirectUri,
      }),
    })
      .then(async (response) => {
        const oauthTokenJson = response.oauthTokenJson || "";
        if (!oauthTokenJson) {
          throw new Error("OAuth token response did not include token JSON.");
        }
        setOauthTokenJson(oauthTokenJson);
        downloadTokenJson(oauthTokenJson);
        sessionStorage.removeItem(SESSION_KEY);
        window.history.replaceState({}, "", window.location.pathname);

        try {
          const taskStatus = await ensureReadGDriveTask(fetchContext);
          const taskMessage =
            taskStatus === "created"
              ? `${READ_G_DRIVE_TASK_NAME} task created.`
              : `${READ_G_DRIVE_TASK_NAME} task is available.`;
          setMessage(`OAuth token downloaded as token.json. ${taskMessage}`);
        } catch (taskError) {
          setMessage("OAuth token downloaded as token.json.");
          setError(
            `${READ_G_DRIVE_TASK_NAME} task was not created: ${formatError(
              taskError
            )}`
          );
        }
      })
      .catch((err) => setError(formatError(err)))
      .finally(() => setLoading(false));
  }, [fetchContext]);

  function handleGenerateOAuth() {
    setError("");
    setMessage("");

    if (!folderId.trim()) {
      setError("Folder ID is required before generating OAuth.");
      return;
    }
    const nextOAuthUrl = buildOAuthUrl({
      folderId: folderId.trim(),
      clientId: clientId.trim(),
    });

    if (!nextOAuthUrl) {
      setError("OAuth client ID is required before generating OAuth.");
      return;
    }
    if (!clientSecret.trim()) {
      setError("OAuth client secret is required before generating OAuth.");
      return;
    }

    sessionStorage.setItem(
      SESSION_KEY,
      JSON.stringify({
        folderId: folderId.trim(),
        clientId: clientId.trim(),
        clientSecret: clientSecret.trim(),
        oauthClientJson: buildOAuthClientJson(
          clientId.trim(),
          clientSecret.trim()
        ),
      })
    );
    window.location.assign(nextOAuthUrl);
  }

  function handleOAuthClientFileChange(event) {
    const file = event.target.files && event.target.files[0];
    if (!file) {
      return;
    }
    file
      .text()
      .then((content) => {
        JSON.parse(content);
        const client = getOAuthClient(content);
        if (!client.clientId || !client.clientSecret) {
          throw new Error(
            "OAuth client JSON must include client_id and client_secret."
          );
        }
        setClientId(client.clientId);
        setClientSecret(client.clientSecret);
        setClientJsonFileName(file.name);
        setMessage(`${file.name} loaded as OAuth client JSON.`);
        setError("");
      })
      .catch((err) =>
        setError(err.message || "OAuth client JSON file is invalid.")
      );
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
      })
      .catch((err) =>
        setError(err.message || "OAuth token JSON file is invalid.")
      );
  }

  function handleLoadDrive() {
    setError("");
    setMessage("");
    setFiles([]);
    setCount(0);

    if (!folderId.trim()) {
      setError("Folder ID is required.");
      return;
    }
    if (!safeParseJson(oauthTokenJson)) {
      setError(
        "OAuth token JSON is required. Generate OAuth or upload token.json first."
      );
      return;
    }

    setLoading(true);
    fetchWithContext("integrations/gdrive/load", fetchContext, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        folderId: folderId.trim(),
        oauthTokenJson,
        maxFiles,
      }),
    })
      .then((response) => {
        setFiles(response.files || []);
        setCount(response.count || 0);
        setMessage("Google Drive folder loaded.");
      })
      .catch((err) => setError(formatError(err)))
      .finally(() => setLoading(false));
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
              <div className={classes.selector}>
                <CloudQueueIcon className={classes.selectorIcon} />
                <Box>
                  <Text level={2}>Google Drive Read</Text>
                  <Text className={classes.muted}>
                    Task: {READ_G_DRIVE_TASK_NAME}
                  </Text>
                  <Text className={classes.muted}>Type: GDRIVE_READ</Text>
                </Box>
              </div>
            </Paper>
          </Grid>
          <Grid item xs={12} md={5}>
            <Paper padded className={classes.card}>
              <div className={classes.formStack}>
                <Box className={classes.actionRow}>
                  <Text level={2}>Google Drive</Text>
                  <Chip size="small" label="Modular task" />
                </Box>
                <Input
                  label="Folder ID"
                  value={folderId}
                  fullWidth
                  variant="outlined"
                  onChange={setFolderId}
                />
                <Input
                  label="OAuth Client ID"
                  value={clientId}
                  fullWidth
                  variant="outlined"
                  onChange={setClientId}
                />
                <Input
                  label="OAuth Client Secret"
                  value={clientSecret}
                  fullWidth
                  variant="outlined"
                  type="password"
                  onChange={setClientSecret}
                />
                <Box className={classes.actionRow}>
                  <input
                    id="gdrive-oauth-client-json-file"
                    className={classes.fileInput}
                    type="file"
                    accept=".json,application/json"
                    onChange={handleOAuthClientFileChange}
                  />
                  <label htmlFor="gdrive-oauth-client-json-file">
                    <Button
                      component="span"
                      variant="outlined"
                      startIcon={<FolderOpenIcon />}
                    >
                      Upload Client JSON
                    </Button>
                  </label>
                  {clientJsonFileName && (
                    <Text className={classes.muted}>{clientJsonFileName}</Text>
                  )}
                </Box>
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
                    <Text className={classes.muted}>{tokenJsonFileName}</Text>
                  )}
                </Box>
                <TextField
                  label="OAuth Token JSON"
                  value={oauthTokenJson}
                  onChange={(event) => setOauthTokenJson(event.target.value)}
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
                  onChange={(value) => setMaxFiles(Math.max(1, Number(value)))}
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
                    onClick={handleLoadDrive}
                    disabled={loading}
                  >
                    Load Drive
                  </Button>
                </Box>
                {message && <Text>{message}</Text>}
                {error && <Text color="error">{error}</Text>}
              </div>
            </Paper>
          </Grid>
          <Grid item xs={12} md={4}>
            <Paper padded className={classes.card}>
              <Text level={2}>Workflow Task</Text>
              <pre className={classes.codeBlock}>{taskSnippet(maxFiles)}</pre>
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
        </Grid>
      </div>
    </>
  );
}
