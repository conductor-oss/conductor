import { Box } from "@mui/material";
import ReactJson from "components/ReactJson";

const EDITOR_THEME_KEY = "editorTheme";

const getReactJsonTheme = () => {
  let localEditorTheme = localStorage.getItem(EDITOR_THEME_KEY);
  if (!localEditorTheme) {
    localEditorTheme = "vs-light";
  }
  let theme = "shapeshifter";
  if (localEditorTheme === "vs-light") {
    theme = "shapeshifter:inverted";
  }
  return theme;
};

export default function ExecutionJson({ execution }) {
  const theme = getReactJsonTheme();
  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        overflow: "auto",
        height: "100%",
        padding: 3,
        pb: 0,
      }}
    >
      <ReactJson
        src={execution}
        initialCollapse={true}
        title="Complete Workflow JSON"
        theme={theme}
        indentWidth={2}
        workflowName={execution.workflowName}
        editorHeight="100%"
      />
    </Box>
  );
}
