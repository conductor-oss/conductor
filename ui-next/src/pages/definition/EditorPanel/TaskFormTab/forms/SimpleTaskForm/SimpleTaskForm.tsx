import { Editor } from "@monaco-editor/react";
import { Box, Grid, IconButton, Link, Stack } from "@mui/material";
import { ArrowsOutSimple, ArrowSquareOut } from "@phosphor-icons/react";
import { Paper, Tab, Tabs } from "components";
import MuiTypography from "components/MuiTypography";
import { SnackbarMessage } from "components/SnackbarMessage";
import UIModal from "components/UIModal";
import { ConductorFlatMapFormBase } from "components/v1/FlatMapForm/ConductorFlatMapForm";
import CopyIcon from "components/v1/icons/CopyIcon";
import { path as _path } from "lodash/fp";
import { useMemo, useState } from "react";
import { getAccessToken } from "shared/auth/tokenManagerJotai";
import { defaultEditorOptions, type EditorOptions } from "shared/editor";
import { updateField } from "utils/fieldHelpers";
import { ConductorCacheOutput } from "../ConductorCacheOutputForm";
import { Optional } from "../OptionalFieldForm";
import { SchemaForm } from "../SchemaForm";
import TaskFormSection from "../TaskFormSection";
import { TaskFormProps } from "../types";
import { useSchemaFormHandler } from "../hooks/useSchemaFormHandler";
import {
  sampleClojureCode,
  sampleCSharpCode,
  sampleGolangCode,
  sampleJavaCode,
  sampleJavaScriptCode,
  samplePythonCode,
  sampleTypeScriptCode,
} from "./SampleCode";
import { TemplateKeys } from "./TemplateKeys";

const inputParametersPath = "inputParameters";
const SAMPLE_CODE_TABS = [
  "Java",
  "Python",
  "Golang",
  "CSharp",
  "JavaScript",
  "TypeScript",
  // "Clojure",
];
const editorOption: EditorOptions = {
  ...defaultEditorOptions,
  tabSize: 2,
  minimap: { enabled: false },
  quickSuggestions: true,
  overviewRulerLanes: 0,
  scrollbar: {
    vertical: "hidden",
    // this property is added because it was not allowing us to scroll when mouse pointer is over this component
    alwaysConsumeMouseWheel: false,
  },
  formatOnType: true,
  readOnly: true,
  wordWrap: "on",
  scrollBeyondLastLine: false,
  automaticLayout: true,
};

const SampleCodeSection = ({
  height = "300px",
  selectedSample,
  setSelectedSample,
  displaySampleCode,
  editorLanguage,
  handleCopy,
}: {
  height?: string;
  selectedSample: string;
  setSelectedSample: (sample: string) => void;
  displaySampleCode: string;
  editorLanguage: string;
  handleCopy: () => void;
}) => {
  return (
    <>
      <Tabs
        value={selectedSample}
        variant="scrollable"
        scrollButtons="false"
        style={{
          marginBottom: 0,
          borderBottom: "1px solid rgba(0,0,0,0.2)",
        }}
        contextual
        onChange={(_event: any, val: any) => setSelectedSample(val)}
      >
        {SAMPLE_CODE_TABS.map((item) => (
          <Tab key={item} value={item} label={item} />
        ))}
      </Tabs>
      <Paper square elevation={0}>
        <Box sx={{ position: "relative", padding: "10px 0" }}>
          <Stack
            sx={{
              position: "absolute",
              bottom: "4px",
              right: "4px",
              zIndex: 10,
            }}
            gap={1}
            flexDirection="row"
          >
            <IconButton
              sx={{
                padding: "1px",
              }}
              onClick={handleCopy}
            >
              <CopyIcon />
            </IconButton>
          </Stack>
          <Editor
            theme={"vs-light"}
            height={height}
            value={displaySampleCode}
            saveViewState
            language={editorLanguage}
            options={editorOption}
          />
        </Box>
      </Paper>
    </>
  );
};

export const SimpleTaskForm = ({ task, onChange }: TaskFormProps) => {
  const [selectedSample, setSelectedSample] = useState("Python");
  const [showAlert, setShowAlert] = useState(false);
  const [showSampleModal, setShowSampleModal] = useState(false);

  const displaySampleCode = useMemo(() => {
    const accessToken = getAccessToken() || "";
    const inputParamKeys = task?.inputParameters
      ? Object.keys(task?.inputParameters)
      : [];
    if (selectedSample === "Java") {
      return sampleJavaCode({
        taskDefName: task?.name ?? "greet",
        inputParamKeys: inputParamKeys,
      });
    }
    if (selectedSample === "Python") {
      return samplePythonCode({
        taskDefName: task?.name ?? "greet",
        inputParamKeys: inputParamKeys,
      });
    }
    if (selectedSample === "Golang") {
      return sampleGolangCode({
        taskDefName: task?.name ?? "greet",
        inputParamKeys: inputParamKeys,
      });
    }
    if (selectedSample === "CSharp") {
      return sampleCSharpCode({
        taskDefName: task?.name ?? "greet",
        inputParamKeys: inputParamKeys,
      });
    }
    if (selectedSample === "JavaScript") {
      return sampleJavaScriptCode({
        taskDefName: task?.name ?? "task_definition_name",
        accessToken,
        inputParamKeys: inputParamKeys,
      });
    }
    if (selectedSample === "TypeScript") {
      return sampleTypeScriptCode({
        taskDefName: task?.name ?? "task_definition_name",
        accessToken,
        inputParamKeys: inputParamKeys,
      });
    }
    if (selectedSample === "Clojure") {
      return sampleClojureCode;
    }
    return "";
  }, [selectedSample, task?.inputParameters, task?.name]);

  const handleCopy = () => {
    setShowAlert(true);
    const selectedCode = displaySampleCode;
    navigator.clipboard.writeText(selectedCode ?? "");
  };

  const editorLanguage = useMemo(() => {
    if (selectedSample === "Golang") {
      return "go";
    }
    return selectedSample.toLowerCase();
  }, [selectedSample]);

  const handleSchemaChange = useSchemaFormHandler({ task, onChange });

  return (
    <Box width="100%">
      {showAlert && (
        <SnackbarMessage
          message="Copied to Clipboard"
          severity="success"
          onDismiss={() => setShowAlert(false)}
          anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
        />
      )}
      <SchemaForm value={task.taskDefinition} onChange={handleSchemaChange} />
      <TaskFormSection
        title="Input parameters"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={12}>
            <ConductorFlatMapFormBase
              showFieldTypes
              keyColumnLabel="Key"
              valueColumnLabel="Value"
              addItemLabel="Add parameter"
              value={_path(inputParametersPath, task)}
              onChange={(value) =>
                onChange(updateField(inputParametersPath, value, task))
              }
            />
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection>
        <Box display="flex" flexDirection="column" gap={3}>
          <ConductorCacheOutput onChange={onChange} taskJson={task} />
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>

      <TemplateKeys
        task={task}
        onUniteParameter={(inputParams: Record<string, unknown>) =>
          onChange({ ...task, inputParameters: inputParams })
        }
      />

      <TaskFormSection>
        <MuiTypography marginTop={3} opacity={0.6} fontWeight={600}>
          Sample worker code
        </MuiTypography>
        <Box sx={{ display: "flex", alignItems: "center", mb: 1.5 }}>
          <MuiTypography>
            <Box component="span" sx={{ opacity: 0.5 }}>
              Generate your auth key and secret from{" "}
            </Box>
            <Link
              href="/applicationManagement/applications"
              target="_blank"
              sx={{ fontWeight: 400, fontSize: "12px" }}
            >
              Applications <ArrowSquareOut size={12} />
            </Link>
          </MuiTypography>
          <IconButton
            onClick={() => setShowSampleModal(true)}
            style={{ marginLeft: "auto" }}
          >
            <ArrowsOutSimple size={16} />
          </IconButton>
        </Box>
        <SampleCodeSection
          selectedSample={selectedSample}
          setSelectedSample={setSelectedSample}
          displaySampleCode={displaySampleCode}
          editorLanguage={editorLanguage}
          handleCopy={handleCopy}
        />
      </TaskFormSection>
      <UIModal
        open={showSampleModal}
        setOpen={setShowSampleModal}
        maxWidth={"xl"}
        title="Sample worker code"
        icon={<CopyIcon />}
        description="All sample worker codes in one place"
        enableCloseButton
      >
        <SampleCodeSection
          height="calc(100vh - 280px)"
          selectedSample={selectedSample}
          setSelectedSample={setSelectedSample}
          displaySampleCode={displaySampleCode}
          editorLanguage={editorLanguage}
          handleCopy={handleCopy}
        />
      </UIModal>
    </Box>
  );
};
