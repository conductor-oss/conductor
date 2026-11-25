import { Alert, Box, CircularProgress, Paper } from "@mui/material";
import { Tab, Tabs } from "components";
import SectionHeader from "components/layout/SectionHeader";
import ReactJson from "components/ReactJson";
import { AgentDefinitionDiagram } from "pages/execution/AgentExecution/AgentDefinitionView";
import { useState } from "react";
import { Helmet } from "react-helmet";
import { useParams } from "react-router";
import { useFetch } from "utils/query";

export default function AgentDefinition() {
  const [selectedTab, setSelectedTab] = useState<"diagram" | "json">("diagram");
  const { name: encodedName, version } = useParams<{
    name: string;
    version?: string;
  }>();
  const name = encodedName ? decodeURIComponent(encodedName) : "";
  const versionQuery = version ? `?version=${encodeURIComponent(version)}` : "";
  const { data, isFetching, isError } = useFetch<Record<string, unknown>>(
    `/agent/${encodeURIComponent(name)}${versionQuery}`,
    { when: Boolean(name) },
  );

  return (
    <>
      <Helmet>
        <title>
          {name ? `${name} | Agent Definition` : "Agent Definition"}
        </title>
      </Helmet>
      <SectionHeader
        title={name ? `Agent Definition: ${name}` : "Agent Definition"}
        _deprecate_marginTop={0}
      />
      <Box sx={{ px: 3, pb: 3 }}>
        <Paper
          variant="outlined"
          sx={{
            height: "calc(100vh - 150px)",
            minHeight: 480,
            overflow: "hidden",
            display: "flex",
            flexDirection: "column",
          }}
        >
          {isFetching && !data ? (
            <Box
              sx={{
                height: "100%",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <CircularProgress size={32} />
            </Box>
          ) : isError || !data ? (
            <Alert severity="error">
              Unable to load this agent definition.
            </Alert>
          ) : (
            <>
              <Tabs
                value={selectedTab}
                contextual
                sx={{ flexShrink: 0, borderBottom: 1, borderColor: "divider" }}
              >
                <Tab
                  label="Diagram"
                  value="diagram"
                  onClick={() => setSelectedTab("diagram")}
                />
                <Tab
                  label="JSON"
                  value="json"
                  onClick={() => setSelectedTab("json")}
                />
              </Tabs>
              <Box sx={{ flex: 1, minHeight: 0, overflow: "hidden" }}>
                {selectedTab === "diagram" ? (
                  <AgentDefinitionDiagram agentDef={data} />
                ) : (
                  <Box sx={{ height: "100%", p: 3, pb: 0 }}>
                    <ReactJson
                      src={data}
                      title="Agent Definition JSON"
                      workflowName={name}
                      editorHeight="100%"
                    />
                  </Box>
                )}
              </Box>
            </>
          )}
        </Paper>
      </Box>
    </>
  );
}
