import { useState } from "react";
import { Button, Grid, Stack } from "@mui/material";
import DownloadIcon from "@mui/icons-material/Download";
import { Input, Tab, Tabs } from "components";
import {
  CodeLanguage,
  JavaLanguageSet,
  OperatingSystemEnvironment,
} from "./types";
import { useConductorProjectBuilder } from "utils/hooks/useConductorProjectBuilder";
import { CodeSnippet } from "./components/CodeSnippet";

export const DEFAULT_TASK_NAME = "my_first_simple_task";

export const GetStartedSample = ({
  serverUrl = "your-server-url-goes-here",
  onTaskNameUpdated,
}: {
  apiKey?: string;
  apiSecret?: string;
  serverUrl?: string;
  environment: OperatingSystemEnvironment;
  onTaskNameUpdated?: (taskName: string) => void;
}) => {
  const [selectedLanguage, setSelectedLanguage] = useState<CodeLanguage>(
    CodeLanguage.JAVA,
  );

  const [selectedJavaLanguageSet, setSelectedJavaLanguageSet] =
    useState<JavaLanguageSet>(JavaLanguageSet.GRADLE);
  const [taskName, setTaskName] = useState<string>(DEFAULT_TASK_NAME);
  const [projectName, setProjectName] = useState<string>(
    "ConductorSampleProject",
  );
  const [packageName, setPackageName] = useState<string>("org.example");
  const [namespace, setNamespace] = useState<string>("");

  const { displayCode, onDownload } = useConductorProjectBuilder({
    serverUrl,
    taskName: taskName || DEFAULT_TASK_NAME,
    language: selectedLanguage,
    languageSet: selectedJavaLanguageSet,
    namespace,
    packageName,
    projectName,
    useEnvVars: true,
  });

  return (
    <>
      <Tabs
        value={selectedLanguage}
        variant="scrollable"
        scrollButtons={false}
        style={{
          marginBottom: 0,
          borderBottom: "1px solid rgba(0,0,0,0.2)",
        }}
        contextual
        onChange={(_event: any, val: any) => setSelectedLanguage(val)}
      >
        {Object.values(CodeLanguage)
          .filter(
            (item) =>
              item !== CodeLanguage.CLOJURE && item !== CodeLanguage.GROOVY,
          )
          .map((item) => (
            <Tab key={item} value={item} label={item} />
          ))}
      </Tabs>
      {selectedLanguage === CodeLanguage.JAVA && (
        <Tabs
          value={selectedJavaLanguageSet}
          variant="scrollable"
          scrollButtons={false}
          style={{
            marginBottom: 0,
            borderBottom: "1px solid rgba(0,0,0,0.2)",
          }}
          contextual
          onChange={(_event: any, val: any) => setSelectedJavaLanguageSet(val)}
        >
          {Object.values(JavaLanguageSet).map((item) => (
            <Tab key={item} value={item} label={item} />
          ))}
        </Tabs>
      )}
      <Stack spacing={4}>
        <Grid sx={{ width: "100%", mt: 2 }} container spacing={2}>
          <Grid size={12}>
            <Grid sx={{ width: "100%", mt: 0 }} container spacing={2}>
              <Grid size={3}>
                <Input
                  label="Task Name"
                  fullWidth
                  value={taskName}
                  onChange={(value) => {
                    setTaskName(value);
                    onTaskNameUpdated?.(value);
                  }}
                />
              </Grid>

              {selectedLanguage === CodeLanguage.JAVA && (
                <Grid size={3}>
                  <Input
                    label="Project Name"
                    fullWidth
                    value={projectName}
                    onChange={(value) => {
                      setProjectName(value);
                    }}
                  />
                </Grid>
              )}

              {[CodeLanguage.JAVA, CodeLanguage.GROOVY].includes(
                selectedLanguage,
              ) && (
                <Grid size={3}>
                  <Input
                    label="Package Name"
                    fullWidth
                    value={packageName}
                    onChange={(value) => {
                      setPackageName(value);
                    }}
                  />
                </Grid>
              )}

              {[CodeLanguage.CSHARP].includes(selectedLanguage) && (
                <Grid size={3}>
                  <Input
                    label="Namespace"
                    fullWidth
                    value={namespace}
                    onChange={(value) => {
                      setNamespace(value);
                    }}
                  />
                </Grid>
              )}
              <Grid size={3}>
                <Button onClick={onDownload} startIcon={<DownloadIcon />}>
                  Download Project
                </Button>
              </Grid>
            </Grid>
          </Grid>
          <Grid size={12}>
            <CodeSnippet code={displayCode} />
          </Grid>
        </Grid>
      </Stack>
    </>
  );
};
