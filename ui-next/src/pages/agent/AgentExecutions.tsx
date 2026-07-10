import { FormControlLabel, Switch } from "@mui/material";
import { DataTable, NavLink, Paper } from "components";
import SectionHeader from "components/layout/SectionHeader";
import Header from "components/ui/Header";
import NoDataComponent from "components/ui/NoDataComponent";
import SectionContainer from "components/ui/layout/SectionContainer";
import { useMemo, useState } from "react";
import { Helmet } from "react-helmet";
import { useSearchParams } from "react-router-dom";
import { AGENT_EXECUTIONS_URL } from "utils/constants/route";
import { useFetch } from "utils/query";
import { AgentExecutionSearchResult, AgentExecutionSummary } from "./types";

const INTRO_CONTENT = `**Agent executions** are AgentSpan agent runs, executed as native Conductor workflows. Click an execution to open it in the workflow viewer.

No agents deployed yet? [Build one with the AgentSpan SDK](https://github.com/agentspan-ai/agentspan).`;

export default function AgentExecutions() {
  const [searchParams] = useSearchParams();
  const agentName = searchParams.get("agentName") || "";
  const [hideSubAgents, setHideSubAgents] = useState(true);

  // Sub-agent/sub-workflow filtering is done server-side: topLevelOnly restricts
  // results to root executions (parentWorkflowId = ""). Off shows every execution.
  const params = new URLSearchParams({ size: "50" });
  if (agentName) params.set("agentName", agentName);
  if (hideSubAgents) params.set("topLevelOnly", "true");
  const path = `/agent/executions?${params.toString()}`;
  const { data, isFetching, refetch } =
    useFetch<AgentExecutionSearchResult>(path);

  const tableData = useMemo<AgentExecutionSummary[]>(
    () => (Array.isArray(data?.results) ? data!.results : []),
    [data],
  );

  const columns = useMemo(
    () => [
      {
        id: "executionId",
        name: "executionId",
        label: "Execution ID",
        grow: 1.5,
        tooltip: "Conductor workflow execution id",
        renderer: (executionId: string) => (
          <NavLink path={`${AGENT_EXECUTIONS_URL.BASE}/${executionId}`}>
            {executionId}
          </NavLink>
        ),
      },
      {
        id: "agentName",
        name: "agentName",
        label: "Agent",
        tooltip: "Agent name",
      },
      {
        id: "status",
        name: "status",
        label: "Status",
        tooltip: "Execution status",
      },
      {
        id: "startTime",
        name: "startTime",
        label: "Start time",
        tooltip: "Execution start time",
      },
      {
        id: "executionTime",
        name: "executionTime",
        label: "Duration (ms)",
        grow: 0.5,
        tooltip: "Execution duration in milliseconds",
      },
      {
        id: "createdBy",
        name: "createdBy",
        label: "Created by",
        tooltip: "Principal that started the execution",
      },
    ],
    [],
  );

  return (
    <>
      <Helmet>
        <title>Agent Executions</title>
      </Helmet>
      <SectionHeader
        title={
          agentName ? `Agent Executions — ${agentName}` : "Agent Executions"
        }
        _deprecate_marginTop={0}
      />
      <SectionContainer>
        {/*@ts-ignore*/}
        <Paper variant="outlined">
          <Header loading={isFetching} />
          <FormControlLabel
            control={
              <Switch
                size="small"
                checked={hideSubAgents}
                onChange={(e) => setHideSubAgents(e.target.checked)}
              />
            }
            label="Hide sub-agent executions"
            sx={{ ml: 1, mb: 1 }}
          />
          <DataTable
            localStorageKey="agentExecutionsTable"
            quickSearchEnabled
            quickSearchPlaceholder="Search executions"
            keyField="executionId"
            data={tableData}
            columns={columns}
            noDataComponent={
              <NoDataComponent
                title="Agent Executions"
                description={INTRO_CONTENT}
                buttonText="Refresh"
                buttonHandler={() => refetch()}
              />
            }
          />
        </Paper>
      </SectionContainer>
    </>
  );
}
