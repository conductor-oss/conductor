import Editor from "@monaco-editor/react";
import { Box } from "@mui/material";
import { DiffEditor } from "components/DiffEditor/DiffEditor";
import { useCallback, useContext, useRef } from "react";
import { defaultEditorOptions } from "shared/editor";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { configureMonaco } from "utils/monacoUtils/CodeEditorUtils";

function ScheduleDiffEditor({
  data,
  newData,
  original,
  handleChange,
  isConfirmingSave,
  handleDiffEditorMount,
}) {
  const { mode } = useContext(ColorModeContext);
  const monacoObjects = useRef(null);
  const minEditor_Width = 590;
  const darkMode = mode === "dark";
  const editorTheme = darkMode ? "vs-dark" : "vs-light";
  const editorState = {
    editorOptions: {
      ...defaultEditorOptions,
      selectOnLineNumbers: true,
    },
  };

  const handleChangeTest = (changedData) => {
    handleChange(changedData);
  };
  const editorDidMount = useCallback(
    (editor) => {
      monacoObjects.current = editor;
    },
    [monacoObjects],
  );
  const handleEditorWillMount = useCallback((monaco) => {
    configureMonaco(monaco);
  }, []);

  return (
    <>
      <Box
        sx={{
          maxWidth: "820px",
          flex: "0 0 auto",
          position: "relative",
          width: "100%",
          height: "100%",
          border: "1px solid #aaaaaa",
          borderTop: "1px solid rgba(0,0,0,.2)",
        }}
      >
        <Box
          sx={{
            display: "flex",
            flexFlow: "column",
            height: "100%",
            overflowX: "auto",
            minWidth: minEditor_Width,
          }}
        >
          {isConfirmingSave ? (
            <DiffEditor
              height={"100%"}
              width={"100%"}
              theme={editorTheme}
              language="json"
              original={JSON.stringify(original, null, 2)}
              modified={newData}
              options={editorState.editorOptions}
              onMount={handleDiffEditorMount}
            />
          ) : (
            <Editor
              height={"100%"}
              width={"100%"}
              language="json"
              theme={editorTheme}
              onMount={editorDidMount}
              options={editorState.editorOptions}
              beforeMount={handleEditorWillMount}
              value={JSON.stringify(data, null, 2)}
              onChange={(maybeText) => {
                handleChangeTest(maybeText);
              }}
            />
          )}
        </Box>
      </Box>
    </>
  );
}
export default ScheduleDiffEditor;
