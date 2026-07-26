import { Alert, Box, Grid } from "@mui/material";
import { Button, DataTable, NavLink, Paper } from "components";
import PlayIcon from "components/icons/PlayIcon";
import ResetIcon from "components/icons/ResetIcon";
import XCloseIcon from "components/icons/XCloseIcon";
import SectionHeader from "components/layout/SectionHeader";
import { ConductorAutoComplete } from "components/ui/inputs/ConductorAutoComplete";
import ConductorInput from "components/ui/inputs/ConductorInput";
import SectionContainer from "components/ui/layout/SectionContainer";
import { useState } from "react";
import { Helmet } from "react-helmet";
import { useNavigate } from "react-router";
import { AGENT_EXECUTIONS_URL } from "utils/constants/route";
import { useAction, useFetch } from "utils/query";
import { useLocalStorage } from "utils";
import { v4 as uuidv4 } from "uuid";
import { AgentSummary } from "./types";

type AgentStartResponse = {
  executionId: string;
  agentName: string;
};

type AgentRunHistory = {
  id: string;
  agentName: string;
  model: string;
  prompt: string;
  executionId: string;
  executionTime: number;
};

/** Starts a deployed agent through POST /api/agent/start. */
export default function RunAgent() {
  const navigate = useNavigate();
  const { data: agents = [] } = useFetch<AgentSummary[]>("/agent/list");
  const [agentName, setAgentName] = useState("");
  const [model, setModel] = useState("");
  const [prompt, setPrompt] = useState("");
  const [started, setStarted] = useState<AgentStartResponse>();
  const [error, setError] = useState("");
  const [agentHistory, setAgentHistory] = useLocalStorage(
    "agentRunHistory",
    [],
  ) as [AgentRunHistory[], (history: AgentRunHistory[]) => void];

  const { mutate: startAgent, isLoading } = useAction<
    AgentStartResponse,
    { body: string }
  >("/agent/start", "post", {
    onSuccess: (response) => {
      setStarted(response);
      setError("");
      setAgentHistory(
        [
          {
            id: uuidv4(),
            agentName,
            model,
            prompt,
            executionId: response.executionId,
            executionTime: Date.now(),
          },
          ...(agentHistory || []),
        ].slice(0, 20),
      );
    },
    onError: async (response) => {
      try {
        const body = await response.json();
        setError(body?.message || "Unable to start agent.");
      } catch {
        setError("Unable to start agent.");
      }
    },
  });

  const reset = () => {
    setAgentName("");
    setModel("");
    setPrompt("");
    setStarted(undefined);
    setError("");
  };

  const run = () => {
    if (!agentName || !prompt.trim()) {
      return;
    }
    setStarted(undefined);
    startAgent({
      body: JSON.stringify({
        name: agentName,
        model: model.trim() || undefined,
        prompt,
      }),
    });
  };

  const agentNames = agents
    .map((agent) => agent.name)
    .sort((a, b) => a.localeCompare(b));

  const restoreHistory = (entry: AgentRunHistory) => {
    setAgentName(entry.agentName);
    setModel(entry.model);
    setPrompt(entry.prompt);
    setStarted(undefined);
    setError("");
  };

  return (
    <>
      <Helmet>
        <title>Run Agent</title>
      </Helmet>
      <SectionContainer
        header={
          <SectionHeader
            _deprecate_marginTop={0}
            title="Run Agent"
            actions={
              <>
                <Button
                  variant="text"
                  onClick={() => navigate(-1)}
                  startIcon={<XCloseIcon />}
                >
                  Close
                </Button>
                <Button
                  variant="text"
                  onClick={reset}
                  startIcon={<ResetIcon />}
                >
                  Reset
                </Button>
                <Button
                  id="run-agent-btn"
                  color="secondary"
                  onClick={run}
                  disabled={!agentName || !prompt.trim() || isLoading}
                  startIcon={<PlayIcon />}
                >
                  Run agent
                </Button>
              </>
            }
          />
        }
      >
        {error && (
          <Alert sx={{ mb: 3 }} severity="error" onClose={() => setError("")}>
            {error}
          </Alert>
        )}
        {started && (
          <Alert
            sx={{ mb: 3 }}
            severity="success"
            onClose={() => setStarted(undefined)}
          >
            Agent execution started:&nbsp;
            <NavLink
              path={`${AGENT_EXECUTIONS_URL.BASE}/${started.executionId}`}
            >
              {started.executionId}
            </NavLink>
          </Alert>
        )}
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 8, lg: 7 }}>
            <Paper variant="outlined" sx={{ p: 4 }}>
              <Grid container spacing={3}>
                <Grid size={12}>
                  <ConductorAutoComplete
                    id="run-agent-name"
                    fullWidth
                    label="Agent"
                    options={agentNames}
                    value={agentName}
                    onChange={(_: unknown, value: string | null) =>
                      setAgentName(value || "")
                    }
                    required
                    autoFocus
                  />
                </Grid>
                <Grid size={12}>
                  <ConductorInput
                    id="run-agent-model"
                    fullWidth
                    label="Model override"
                    placeholder="Use the deployed agent model"
                    value={model}
                    onTextInputChange={setModel}
                    helperText="Optional. This applies only to this execution."
                  />
                </Grid>
                <Grid size={12}>
                  <ConductorInput
                    id="run-agent-prompt"
                    fullWidth
                    required
                    multiline
                    minRows={8}
                    label="Input text"
                    placeholder="What should this agent do?"
                    value={prompt}
                    onTextInputChange={setPrompt}
                  />
                </Grid>
              </Grid>
            </Paper>
          </Grid>
          <Grid size={{ xs: 12, md: 4, lg: 5 }}>
            <Paper variant="outlined" sx={{ width: "100%" }}>
              <DataTable
                title="Agent run history"
                pagination={false}
                defaultShowColumns={["agentName", "executionTime", "restore"]}
                defaultSortFieldId="executionTime"
                defaultSortAsc={false}
                noDataComponent={<Box sx={{ p: 4 }}>History is empty</Box>}
                columns={[
                  {
                    id: "agentName",
                    name: "agentName",
                    label: "Agent",
                    renderer: (value: string, row: AgentRunHistory) => (
                      <NavLink
                        path={`${AGENT_EXECUTIONS_URL.BASE}/${row.executionId}`}
                      >
                        {value}
                      </NavLink>
                    ),
                  },
                  {
                    id: "executionTime",
                    name: "executionTime",
                    label: "Started",
                    renderer: (value: number) =>
                      new Date(value).toLocaleString(),
                  },
                  {
                    id: "restore",
                    name: "restore",
                    label: "Reuse",
                    renderer: (_: unknown, row: AgentRunHistory) => (
                      <Button size="small" onClick={() => restoreHistory(row)}>
                        Reuse
                      </Button>
                    ),
                  },
                ]}
                data={agentHistory || []}
                actions={[
                  <Button
                    key="clear-agent-history"
                    size="small"
                    color="tertiary"
                    disabled={!agentHistory?.length}
                    onClick={() => setAgentHistory([])}
                  >
                    Clear history
                  </Button>,
                ]}
              />
            </Paper>
          </Grid>
        </Grid>
      </SectionContainer>
    </>
  );
}
