import { DataTable, Paper } from "components";
import { ColumnCustomType } from "components/ui/DataTable/types";
import SectionHeader from "components/layout/SectionHeader";
import Header from "components/ui/Header";
import NoDataComponent from "components/ui/NoDataComponent";
import SectionContainer from "components/ui/layout/SectionContainer";
import { useMemo } from "react";
import { Helmet } from "react-helmet";
import { useFetch } from "utils/query";
import { SkillSummary } from "./types";

const INTRO_CONTENT = `**Skills** are reusable SKILL.md packages registered with the AgentSpan runtime. Package bytes and metadata are persisted through Conductor's configured backend.`;

export default function Skills() {
  const { data, isFetching, refetch } = useFetch<SkillSummary[]>("/skills");

  const tableData = useMemo<SkillSummary[]>(
    () => (Array.isArray(data) ? data : []),
    [data],
  );

  const columns = useMemo(
    () => [
      {
        id: "name",
        name: "name",
        label: "Skill name",
        tooltip: "Skill name",
      },
      {
        id: "version",
        name: "version",
        label: "Version",
        grow: 0.5,
        tooltip: "Skill version",
      },
      {
        id: "description",
        name: "description",
        label: "Description",
        grow: 2,
        tooltip: "Skill description",
      },
      {
        id: "status",
        name: "status",
        label: "Status",
        grow: 0.5,
        tooltip: "Skill status",
      },
      {
        id: "fileCount",
        name: "fileCount",
        label: "Files",
        grow: 0.5,
        tooltip: "Number of files in the package",
      },
      {
        id: "updatedAt",
        name: "updatedAt",
        label: "Updated",
        type: ColumnCustomType.DATE,
        tooltip: "Last updated time",
      },
    ],
    [],
  );

  return (
    <>
      <Helmet>
        <title>Skills</title>
      </Helmet>
      <SectionHeader title="Skills" _deprecate_marginTop={0} />
      <SectionContainer>
        {/*@ts-ignore*/}
        <Paper variant="outlined">
          <Header loading={isFetching} />
          <DataTable
            localStorageKey="agentSkillsTable"
            quickSearchEnabled
            quickSearchPlaceholder="Search skills"
            keyField="name"
            data={tableData}
            columns={columns}
            noDataComponent={
              <NoDataComponent
                title="Skills"
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
