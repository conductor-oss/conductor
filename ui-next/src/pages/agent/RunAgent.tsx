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
import { Controller, SubmitHandler, useForm } from "react-hook-form";
import { useLocation, useNavigate } from "react-router";
import { AGENT_EXECUTIONS_URL } from "utils/constants/route";
import { useAction, useFetch } from "utils/query";
import { useLocalStorage } from "utils";
import { v4 as uuidv4 } from "uuid";
import { AgentSummary } from "./types";
import { useAiModelOptions } from "./hooks/useAiModelOptions";

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

type RunAgentForm = {
  agentName: string;
  agentVersion?: number;
  model: string;
  prompt: string;
  questions: string;
};

const QUESTIONS_ERROR = "Enter a nonempty JSON object of decision questions.";

function parseDecisionQuestions(value: string): Record<string, unknown> {
  const parsed: unknown = JSON.parse(value);
  if (
    !parsed ||
    typeof parsed !== "object" ||
    Array.isArray(parsed) ||
    !Object.keys(parsed).length
  ) {
    throw new Error(QUESTIONS_ERROR);
  }
  return parsed as Record<string, unknown>;
}

/** Starts a deployed agent through POST /api/agent/start. */
export default function RunAgent() {
  const navigate = useNavigate();
  const location = useLocation();
  const selectedAgent = location.state as {
    agentName?: string;
    agentVersion?: number;
  } | null;
  const { data: agents = [] } = useFetch<AgentSummary[]>("/agent/list");
  const modelOptions = useAiModelOptions();

  const {
    control,
    handleSubmit,
    reset: resetForm,
    setValue,
    watch,
  } = useForm<RunAgentForm>({
    mode: "onChange",
    defaultValues: {
      agentName: selectedAgent?.agentName ?? "",
      agentVersion: selectedAgent?.agentVersion,
      model: "",
      prompt: "",
      questions: "",
    },
  });
  const { agentName, agentVersion, model, prompt } = watch();
  const {
    data: definition,
    isError: definitionError,
    isFetching: definitionLoading,
  } = useFetch<Record<string, unknown>>(
    `/agent/${encodeURIComponent(agentName)}${agentVersion ? `?version=${agentVersion}` : ""}`,
    { when: Boolean(agentName) },
  );
  const isDecision = !definitionLoading && definition?.kind === "decision";
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
    resetForm({
      agentName: "",
      agentVersion: undefined,
      model: "",
      prompt: "",
      questions: "",
    });
    setStarted(undefined);
    setError("");
  };

  const run: SubmitHandler<RunAgentForm> = (values) => {
    setStarted(undefined);
    let context: Record<string, unknown> | undefined;
    if (isDecision && !definition?.questions) {
      try {
        context = { questions: parseDecisionQuestions(values.questions) };
      } catch {
        // The Controller validates this first; keep submission safe if its
        // mounted state changes between validation and this callback.
        setError(QUESTIONS_ERROR);
        return;
      }
    }
    startAgent({
      body: JSON.stringify({
        name: values.agentName,
        ...(context ? { context } : {}),
        version: values.agentVersion,
        model: values.model.trim() || undefined,
        prompt: values.prompt,
      }),
    });
  };

  const agentNames = agents
    .map((agent) => agent.name)
    .sort((a, b) => a.localeCompare(b));

  const restoreHistory = (entry: AgentRunHistory) => {
    resetForm({
      agentName: entry.agentName,
      agentVersion: undefined,
      model: entry.model,
      prompt: entry.prompt,
      questions: "",
    });
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
                  onClick={handleSubmit(run)}
                  disabled={
                    !agentName ||
                    !prompt.trim() ||
                    isLoading ||
                    definitionLoading ||
                    definitionError
                  }
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
        {agentName && definitionError && (
          <Alert sx={{ mb: 3 }} severity="error">
            Unable to load this agent definition. Select the agent again or
            retry later.
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
                  <Controller
                    name="agentName"
                    control={control}
                    rules={{ required: "Select an agent." }}
                    render={({ field }) => (
                      <ConductorAutoComplete
                        id="run-agent-name"
                        fullWidth
                        label="Agent"
                        options={agentNames}
                        value={field.value}
                        onChange={(_: unknown, value: string | null) => {
                          field.onChange(value ?? "");
                          setValue("agentVersion", undefined);
                          setValue("questions", "");
                        }}
                        required
                        autoFocus
                      />
                    )}
                  />
                </Grid>
                <Grid size={12}>
                  <Controller
                    name="model"
                    control={control}
                    render={({ field }) => (
                      <ConductorAutoComplete
                        id="run-agent-model"
                        fullWidth
                        freeSolo
                        label="Model override (optional)"
                        placeholder="Use the deployed agent model"
                        value={field.value}
                        options={isDecision ? [] : modelOptions}
                        groupBy={(option: string) => option.split("/")[0]}
                        onChange={(_: unknown, newValue: string | null) => {
                          field.onChange(newValue ?? "");
                        }}
                        onInputChange={(_: unknown, newValue: string) => {
                          field.onChange(newValue);
                        }}
                        helperText="This applies only to this execution."
                      />
                    )}
                  />
                </Grid>
                <Grid size={12}>
                  <Controller
                    name="prompt"
                    control={control}
                    rules={{
                      validate: (value) =>
                        value.trim().length > 0 || "Input cannot be blank.",
                    }}
                    render={({ field, fieldState }) => (
                      <ConductorInput
                        id="run-agent-prompt"
                        fullWidth
                        required
                        multiline
                        minRows={8}
                        label={isDecision ? "Decision state" : "Input text"}
                        placeholder={
                          isDecision
                            ? "State to evaluate (text or JSON)"
                            : "What should this agent do?"
                        }
                        value={field.value}
                        onTextInputChange={field.onChange}
                        error={!!fieldState.error}
                        helperText={fieldState.error?.message}
                      />
                    )}
                  />
                </Grid>
                {isDecision && (
                  <Grid size={12}>
                    {definition?.questions != null ? (
                      <Box
                        component="pre"
                        sx={{
                          whiteSpace: "pre-wrap",
                          overflowWrap: "anywhere",
                        }}
                        aria-label="Configured decision questions"
                      >
                        {JSON.stringify(definition.questions, null, 2)}
                      </Box>
                    ) : (
                      <Controller
                        name="questions"
                        control={control}
                        rules={{
                          validate: (value) => {
                            try {
                              parseDecisionQuestions(value);
                              return true;
                            } catch {
                              return QUESTIONS_ERROR;
                            }
                          },
                        }}
                        render={({ field, fieldState }) => (
                          <ConductorInput
                            id="run-agent-questions"
                            fullWidth
                            required
                            multiline
                            minRows={6}
                            label="Decision questions (JSON)"
                            value={field.value}
                            onTextInputChange={field.onChange}
                            error={!!fieldState.error}
                            helperText={fieldState.error?.message}
                          />
                        )}
                      />
                    )}
                  </Grid>
                )}
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
