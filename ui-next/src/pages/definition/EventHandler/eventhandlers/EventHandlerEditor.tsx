import Editor from "@monaco-editor/react";
import { Box } from "@mui/material";
import { DiffEditor } from "components/ui/DiffEditor";
import { useContext, useRef } from "react";
import { defaultEditorOptions } from "shared/editor";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { configureMonaco } from "utils/monacoUtils/CodeEditorUtils";

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
    </>
  );
};

export default EventHandlerEditor;
