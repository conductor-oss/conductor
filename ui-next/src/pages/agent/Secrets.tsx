import { Alert, Box } from "@mui/material";
import { DataTable, Paper } from "components";
import SectionHeader from "components/layout/SectionHeader";
import Header from "components/ui/Header";
import NoDataComponent from "components/ui/NoDataComponent";
import SectionContainer from "components/ui/layout/SectionContainer";
import { useMemo } from "react";
import { Helmet } from "react-helmet";
import { useFetch } from "utils/query";
import { CredentialMeta } from "./types";

const INTRO_CONTENT = `**Secrets** are the provider credentials available to agents (e.g. OPENAI_API_KEY).

In OSS Conductor these are read-only here: set them as environment variables on the server so they are injected at runtime, then restart.`;

export default function Secrets() {
  const { data, isFetching, refetch } =
    useFetch<CredentialMeta[]>("/secrets/v2");

  const tableData = useMemo<CredentialMeta[]>(
    () => (Array.isArray(data) ? data : []),
    [data],
  );

  const columns = useMemo(
    () => [
      {
        id: "name",
        name: "name",
        label: "Secret name",
        grow: 2,
        tooltip: "Secret name",
      },
      {
        id: "partial",
        name: "partial",
        label: "Value (masked)",
        sortable: false,
        tooltip: "Masked preview of the secret value",
      },
    ],
    [],
  );

  return (
    <>
      <Helmet>
        <title>Agent Secrets</title>
      </Helmet>
      <SectionHeader title="Agent Secrets" _deprecate_marginTop={0} />
      <SectionContainer>
        <Box sx={{ mb: 2 }}>
          <Alert severity="info">
            Secrets are injected from the server environment (e.g.
            OPENAI_API_KEY) and are read-only here. Set them as environment
            variables and restart the server to change them.
          </Alert>
        </Box>
        {/*@ts-ignore*/}
        <Paper variant="outlined">
          <Header loading={isFetching} />
          <DataTable
            localStorageKey="agentSecretsTable"
            quickSearchEnabled
            quickSearchPlaceholder="Search secrets"
            keyField="name"
            data={tableData}
            columns={columns}
            noDataComponent={
              <NoDataComponent
                title="Agent Secrets"
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
