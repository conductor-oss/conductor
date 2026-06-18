import React, { useEffect, useMemo, useState } from "react";
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

const useStyles = makeStyles({
  root: {
    minHeight: "100%",
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
    redirectUri:
      window.location.origin +
      window.location.pathname.replace(/\/+$/, "") +
      window.location.search.replace(/\?.*$/, ""),
  };
}

function buildOAuthUrl({ folderId, oauthJson }) {
  const { clientId, redirectUri } = getOAuthClient(oauthJson);
  if (!clientId) {
    return "";
  }

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
      name: "GDRIVE_READ",
      taskReferenceName: "gdrive_read_ref",
      type: "GDRIVE_READ",
      inputParameters: {
        folderId: workflowInput("driveFolderId"),
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

export default function GDriveIntegrations() {
  const classes = useStyles();
  const fetchContext = useFetchContext();
  const [folderId, setFolderId] = useState("");
  const [oauthJson, setOauthJson] = useState("");
  const [jsonFileName, setJsonFileName] = useState("");
  const [maxFiles, setMaxFiles] = useState(100);
  const [files, setFiles] = useState([]);
  const [count, setCount] = useState(0);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  const oauthUrl = useMemo(
    () => buildOAuthUrl({ folderId, oauthJson }),
    [folderId, oauthJson]
  );

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get("code");
    if (!code) {
      return;
    }

    const stored = safeParseJson(sessionStorage.getItem(SESSION_KEY)) || {};
    const storedJson = stored.oauthJson || "";
    const storedFolderId = stored.folderId || params.get("state") || "";
    const redirectUri =
      window.location.origin + window.location.pathname.replace(/\/+$/, "");

    setFolderId(storedFolderId);
    setOauthJson(storedJson);
    setLoading(true);
    setError("");
    setMessage("Completing Google OAuth...");

    fetchWithContext("integrations/gdrive/oauth/token", fetchContext, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        authorizationCode: code,
        oauthClientJson: storedJson,
        redirectUri,
      }),
    })
      .then((response) => {
        setOauthJson(response.oauthTokenJson);
        setMessage("OAuth token JSON is ready.");
        sessionStorage.removeItem(SESSION_KEY);
        window.history.replaceState({}, "", window.location.pathname);
      })
      .catch((err) => setError(formatError(err)))
      .finally(() => setLoading(false));
  }, [fetchContext]);

  function handleOAuthStart(event) {
    if (!oauthUrl) {
      event.preventDefault();
      setError("Upload or paste OAuth client JSON before starting OAuth.");
      return;
    }

    sessionStorage.setItem(
      SESSION_KEY,
      JSON.stringify({
        folderId,
        oauthJson,
      })
    );
  }

  function handleFileChange(event) {
    const file = event.target.files && event.target.files[0];
    if (!file) {
      return;
    }
    file
      .text()
      .then((content) => {
        JSON.parse(content);
        setOauthJson(content);
        setJsonFileName(file.name);
        setMessage(`${file.name} loaded.`);
        setError("");
      })
      .catch(() => setError("JSON file is invalid."));
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
    if (!safeParseJson(oauthJson)) {
      setError("OAuth token JSON is required.");
      return;
    }

    setLoading(true);
    fetchWithContext("integrations/gdrive/load", fetchContext, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        folderId: folderId.trim(),
        oauthTokenJson: oauthJson,
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
                  <Text className={classes.muted}>Task: GDRIVE_READ</Text>
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
                <Box className={classes.actionRow}>
                  <input
                    id="gdrive-oauth-json-file"
                    className={classes.fileInput}
                    type="file"
                    accept=".json,application/json"
                    onChange={handleFileChange}
                  />
                  <label htmlFor="gdrive-oauth-json-file">
                    <Button
                      component="span"
                      variant="outlined"
                      startIcon={<FolderOpenIcon />}
                    >
                      Select JSON File
                    </Button>
                  </label>
                  {jsonFileName && (
                    <Text className={classes.muted}>{jsonFileName}</Text>
                  )}
                </Box>
                <TextField
                  label="OAuth Token JSON"
                  value={oauthJson}
                  onChange={(event) => setOauthJson(event.target.value)}
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
                    onClick={handleLoadDrive}
                    disabled={loading}
                  >
                    Load Drive
                  </Button>
                  <Button
                    color="primary"
                    variant="outlined"
                    component="a"
                    href={oauthUrl || "#"}
                    onClick={handleOAuthStart}
                    disabled={!oauthUrl || loading}
                  >
                    Start Google OAuth
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
