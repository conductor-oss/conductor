import { DataTable, NavLink, Paper } from "components";
import SectionHeader from "components/layout/SectionHeader";
import Header from "components/ui/Header";
import NoDataComponent from "components/ui/NoDataComponent";
import SectionContainer from "components/ui/layout/SectionContainer";
import { useMemo } from "react";
import { Helmet } from "react-helmet";
import { useSearchParams } from "react-router-dom";
import { useFetch } from "utils/query";
import { AgentExecutionSearchResult, AgentExecutionSummary } from "./types";

const INTRO_CONTENT = `**Agent executions** are AgentSpan agent runs, executed as native Conductor workflows. Click an execution to open it in the workflow viewer.`;

export default function AgentExecutions() {
  const [searchParams] = useSearchParams();
  const agentName = searchParams.get("agentName") || "";

  const path = agentName
    ? `/agent/executions?size=50&agentName=${encodeURIComponent(agentName)}`
    : "/agent/executions?size=50";
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
          <NavLink path={`/execution/${executionId}`}>{executionId}</NavLink>
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
