import Editor from "@monaco-editor/react";
import { Box } from "@mui/material";
import { DiffEditor } from "components/ui/DiffEditor";
import { useContext, useRef } from "react";
import { defaultEditorOptions } from "shared/editor";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { configureMonaco } from "utils/monacoUtils/CodeEditorUtils";
import { tabColumnStyle, tabSurfaceStyle } from "./tabLayout";

type Props = {
  handleEditChanges?: (code: string) => void;
  editorChanges?: string;
  isConfirmSave?: boolean;
  originalSource?: string;
};

const EventHandlerEditor = ({
  handleEditChanges,
  editorChanges,
  isConfirmSave,
  originalSource,
}: Props) => {
  const { mode } = useContext(ColorModeContext);
  const editorTheme = mode === "dark" ? "vs-dark" : "vs-light";

  const monacoObjects = useRef<any>(null);

  function handleEditorWillMount(monaco: any) {
    configureMonaco(monaco);
  }

  const handleEditorDidMount = (editor: any) => {
    monacoObjects.current = editor;
    if (handleEditChanges) {
      handleEditChanges(editor.getValue());
    }

    monacoObjects.current.onDidChangeModelContent(() => {
      if (handleEditChanges) {
        handleEditChanges(editor.getValue());
      }
    });
  };
  return (
    <Box sx={{ ...tabSurfaceStyle, height: "100%" }}>
      <Box
        sx={{
          ...tabColumnStyle,
          position: "relative",
          height: "100%",
          border: (theme) => `1px solid ${theme.palette.divider}`,
          borderRadius: 1,
          overflow: "hidden",
        }}
      >
        <Box
          sx={{
            display: "flex",
            flexFlow: "column",
            height: "100%",
            overflowX: "auto",
            minWidth: 590,
          }}
        >
          {isConfirmSave ? (
            <DiffEditor
              height={"100%"}
              width={"100%"}
              language="json"
              original={originalSource ? originalSource : ""}
              modified={editorChanges ? editorChanges : ""}
              theme={editorTheme}
              // options={editorState.editorOptions}
            />
          ) : (
            <Editor
              height="100%"
              width="100%"
              language="json"
              theme={editorTheme}
              value={editorChanges}
              beforeMount={handleEditorWillMount}
              onMount={handleEditorDidMount}
              options={{
                ...defaultEditorOptions,
                selectOnLineNumbers: true,
                minimap: {
                  enabled: false,
                },
              }}
              onChange={(maybeText) => {
                if (typeof maybeText === "string") {
                  if (handleEditChanges) {
                    handleEditChanges(maybeText);
                  }
                }
              }}
            />
          )}
        </Box>
      </Box>
    </Box>
  );
};

export default EventHandlerEditor;
