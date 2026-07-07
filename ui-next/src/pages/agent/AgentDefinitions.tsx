import { DataTable, NavLink, Paper } from "components";
import { ColumnCustomType } from "components/ui/DataTable/types";
import SectionHeader from "components/layout/SectionHeader";
import Header from "components/ui/Header";
import NoDataComponent from "components/ui/NoDataComponent";
import SectionContainer from "components/ui/layout/SectionContainer";
import { useMemo } from "react";
import { Helmet } from "react-helmet";
import { AGENT_EXECUTIONS_URL } from "utils/constants/route";
import { useFetch } from "utils/query";
import { AgentSummary } from "./types";

const INTRO_CONTENT = `**Agents** are AI agent definitions compiled and run as native Conductor workflows by the embedded AgentSpan runtime.

Deploy agents with the AgentSpan SDK or CLI, then run and observe them here.`;

export default function AgentDefinitions() {
  const { data, isFetching, refetch } = useFetch<AgentSummary[]>("/agent/list");

  const columns = useMemo(
    () => [
      {
        id: "name",
        name: "name",
        label: "Agent name",
        tooltip: "Agent name",
        renderer: (name: string) => (
          <NavLink
            path={`${AGENT_EXECUTIONS_URL}?agentName=${encodeURIComponent(name)}`}
          >
            {name}
          </NavLink>
        ),
      },
      {
        id: "version",
        name: "version",
        label: "Version",
        grow: 0.5,
        tooltip: "Agent version",
      },
      {
        id: "description",
        name: "description",
        label: "Description",
        grow: 2,
        tooltip: "Agent description",
      },
      {
        id: "type",
        name: "type",
        label: "Type",
        tooltip: "Agent workflow type",
      },
      {
        id: "tags",
        name: "tags",
        label: "Tags",
        type: ColumnCustomType.JSON,
        sortable: false,
        tooltip: "Agent tags",
      },
      {
        id: "createTime",
        name: "createTime",
        label: "Created",
        type: ColumnCustomType.DATE,
        tooltip: "Created time",
      },
    ],
    [],
  );

  const tableData = useMemo<AgentSummary[]>(
    () => (Array.isArray(data) ? data : []),
    [data],
  );

  return (
    <>
      <Helmet>
        <title>Agents</title>
      </Helmet>
      <SectionHeader title="Agents" _deprecate_marginTop={0} />
      <SectionContainer>
        {/*@ts-ignore*/}
        <Paper variant="outlined">
          <Header loading={isFetching} />
          <DataTable
            localStorageKey="agentDefinitionsTable"
            quickSearchEnabled
            quickSearchPlaceholder="Search agents"
            keyField="name"
            data={tableData}
            columns={columns}
            customActions={[]}
            noDataComponent={
              <NoDataComponent
                title="Agents"
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
