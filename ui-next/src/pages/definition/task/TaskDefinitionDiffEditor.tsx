import Editor, { Monaco } from "@monaco-editor/react";
import { Box } from "@mui/material";
import { DiffEditor } from "components/ui/DiffEditor";
import { useTaskDefinition } from "pages/definition/task/state/hook";
import { TaskDefinitionDiffEditorProps } from "pages/definition/task/state/types";
import {
  ForwardedRef,
  forwardRef,
  useCallback,
  useContext,
  useImperativeHandle,
  useRef,
} from "react";
import { defaultEditorOptions } from "shared/editor";
import { ColorModeContext } from "theme/material/ColorModeContext";
import {
  configureMonaco,
  JSON_FILE_TASK_NAME,
} from "utils/monacoUtils/CodeEditorUtils";

const minEditor_Width = 590;
const TaskDefinitionDiffEditor = (
  { taskDefActor }: TaskDefinitionDiffEditorProps,
  editorRefs: ForwardedRef<Monaco>,
) => {
  const { mode } = useContext(ColorModeContext);
  const monacoObjects = useRef<Monaco>(null);
  const diffMonacoObjects = useRef<Monaco>(null);
  const [
    {
      isNewTaskDef,
      modifiedTaskDefinitionString,
      originTaskDefinitionString,
      isConfirmingSave,
    },
    { handleChangeTaskDefinition },
  ] = useTaskDefinition(taskDefActor);

  // const errorKeys = error ? Object.keys(error) : [];
  useImperativeHandle(
    editorRefs,
    () => ({
      reset: (value: string) => {
        monacoObjects.current.setValue(value);
        diffMonacoObjects.current.getModel().modified.setValue(value);
      },
      getValue: () => {
        return monacoObjects.current.getValue();
      },
      code: monacoObjects.current,
      diff: diffMonacoObjects.current,
    }),
    [monacoObjects, diffMonacoObjects],
  );
  const darkMode = mode === "dark";
  const editorTheme = darkMode ? "vs-dark" : "vs-light";
  const editorState = {
    editorOptions: {
      ...defaultEditorOptions,
      selectOnLineNumbers: true,
    },
  } as Monaco;
  const editorDidMount = useCallback(
    (editor: Monaco) => {
      monacoObjects.current = editor;
    },
    [monacoObjects],
  );
  const diffEditorDidMount = useCallback(
    (editor: Monaco) => {
      diffMonacoObjects.current = editor;
      const modifiedEditor = editor.getModifiedEditor();
      modifiedEditor.onDidChangeModelContent((_: any) => {
        const maybeText = modifiedEditor.getValue();
        if (typeof maybeText === "string") {
          handleChangeTaskDefinition(maybeText);
        }
      });
    },
    [diffMonacoObjects, handleChangeTaskDefinition],
  );
  const handleEditorWillMount = useCallback((monaco: Monaco) => {
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
              original={isNewTaskDef ? "" : originTaskDefinitionString}
              modified={modifiedTaskDefinitionString}
              onMount={diffEditorDidMount}
              options={editorState.editorOptions}
            />
          ) : (
            <Editor
              width={"100%"}
              theme={editorState.editorTheme}
              language="json"
              value={modifiedTaskDefinitionString}
              beforeMount={handleEditorWillMount}
              onMount={editorDidMount}
              options={editorState.editorOptions}
              onChange={(maybeText) => {
                if (typeof maybeText === "string") {
                  handleChangeTaskDefinition(maybeText);
                }
              }}
              path={JSON_FILE_TASK_NAME}
            />
          )}
        </Box>
      </Box>
    </>
  );
};
export default forwardRef(TaskDefinitionDiffEditor);
