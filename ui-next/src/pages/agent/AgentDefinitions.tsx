import { DataTable, NavLink, Paper } from "components";
import { ColumnCustomType } from "components/ui/DataTable/types";
import SectionHeader from "components/layout/SectionHeader";
import SectionHeaderActions from "components/ui/layout/SectionHeaderActions";
import AddIcon from "components/icons/AddIcon";
import Header from "components/ui/Header";
import NoDataComponent from "components/ui/NoDataComponent";
import SectionContainer from "components/ui/layout/SectionContainer";
import { useMemo } from "react";
import { Helmet } from "react-helmet";
import { useNavigate } from "react-router";
import { AGENT_DEFINITION_URL } from "utils/constants/route";
import { useFetch } from "utils/query";
import { AgentSummary } from "./types";

const INTRO_CONTENT = `**Agents** are AI agent definitions compiled and run as native Conductor workflows by the embedded AgentSpan runtime.

No agents deployed yet? Use **Create Agent** for a copy-and-run SDK guide.`;

export default function AgentDefinitions() {
  const { data, isFetching, refetch } = useFetch<AgentSummary[]>("/agent/list");
  const navigate = useNavigate();

  const columns = useMemo(
    () => [
      {
        id: "name",
        name: "name",
        label: "Agent name",
        tooltip: "Agent name",
        renderer: (name: string, agent: AgentSummary) => (
          <NavLink
            path={`${AGENT_DEFINITION_URL.BASE}/${encodeURIComponent(name.trim())}/${agent.version}`}
          >
            {name.trim()}
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
      <SectionHeader
        title="Agents"
        _deprecate_marginTop={0}
        actions={
          <SectionHeaderActions
            buttons={[
              {
                label: "Create Agent",
                color: "secondary",
                onClick: () =>
                  navigate(
                    `${AGENT_DEFINITION_URL.NEW}?language=python&framework=native`,
                  ),
                startIcon: <AddIcon />,
              },
            ]}
          />
        }
      />
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
