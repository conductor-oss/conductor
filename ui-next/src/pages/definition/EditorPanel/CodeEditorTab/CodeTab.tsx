import Editor, { Monaco } from "@monaco-editor/react";
import { Box, IconButton, Tooltip } from "@mui/material";
import CopyIcon from "components/icons/CopyIcon";
import _isNil from "lodash/isNil";
import {
  FunctionComponent,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { defaultEditorOptions } from "shared/editor";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors } from "theme/tokens/variables";
import {
  configureMonaco,
  JSON_FILE_NAME,
} from "utils/monacoUtils/CodeEditorUtils";
import { ActorRef } from "xstate";
import { ConfirmSaveDiffEditor } from "../../confirmSave";
import { SaveWorkflowEvents } from "../../confirmSave/state/types";
import "./MonacoDefinitionOverrides.scss";
import { useCodeTabActor } from "./state/hook";
import { CodeMachineEvents } from "./state/types";

const editorState = {
  editorOptions: {
    ...defaultEditorOptions,
    selectOnLineNumbers: true,
  },
};

interface ConfirmSaveEditorWithActorProps {
  saveChangesActor: ActorRef<SaveWorkflowEvents>;
  editorTheme: "vs-dark" | "vs-light";
}

const ConfirmSaveEditorWithActor: FunctionComponent<
  ConfirmSaveEditorWithActorProps
> = ({ saveChangesActor, editorTheme }) => (
  <ConfirmSaveDiffEditor
    saveChangesActor={saveChangesActor}
    editorTheme={editorTheme}
    editorState={editorState}
  />
);

interface CodeTabWithActorProps {
  codeTabActor: ActorRef<CodeMachineEvents>;
  editorTheme: "vs-dark" | "vs-light";
}

const CodeTabWithActor: FunctionComponent<CodeTabWithActorProps> = ({
  codeTabActor,
  editorTheme,
}) => {
  const monacoObjects = useRef(null);
  const [
    { editorChanges, referenceText, shouldTakeToFirstError },
    { handleEditChanges },
  ] = useCodeTabActor(codeTabActor);

  useEffect(() => {
    //Listens to state change. on State change marks the error
    const editor: Monaco = monacoObjects.current;
    if (shouldTakeToFirstError && editor) {
      editor.trigger("keyboard", "editor.action.marker.next", {});
    }
  }, [shouldTakeToFirstError, monacoObjects]);

  const highlightTextReference = useCallback(() => {
    const editor: Monaco = monacoObjects.current;

    if (_isNil(editor) || _isNil(referenceText)) return;

    editor.focus();
    const matches = editor
      .getModel()
      .findMatches(
        referenceText?.textReference,
        true,
        false,
        false,
        null,
        true,
      );
    if (matches) {
      const match = matches[0];
      if (match) {
        editor.setPosition({
          column: match.range.startColumn,
          lineNumber: match.range.startLineNumber,
        });

        editor.revealLineInCenter(match.range.startLineNumber);

        const { linesDecorationsClassName, inlineClassName } =
          referenceText?.referenceReason === "error"
            ? {
                linesDecorationsClassName: "ErrorRefStringLineDecoration",
                inlineClassName: "ErrorRefStringInLineDecoration",
              }
            : {
                linesDecorationsClassName: "TaskNameLineDecoration",
                inlineClassName: "TaskNameInlineDecoration",
              };

        editor.deltaDecorations(
          [],
          [
            {
              range: match.range,
              options: {
                isWholeLine: true,
                linesDecorationsClassName,
              },
            },
            {
              range: match.range,
              options: { inlineClassName },
            },
          ],
        );
      }
    }
  }, [referenceText]);

  const handleEditorWillMount = useCallback((monaco: Monaco) => {
    configureMonaco(monaco);
    monaco.editor.defineTheme("vs-light", {
      base: "vs",
      inherit: true,
      rules: [
        {
          token: "number",
          foreground: colors.primaryGreen,
        },
      ],
      colors: {},
    });
  }, []);

  const editorDidMount = useCallback(
    (editor: Monaco) => {
      monacoObjects.current = editor;
      highlightTextReference();
    },
    [monacoObjects, highlightTextReference],
  );

  // Props to MonacoEditor
  useEffect(() => {
    if (monacoObjects.current && referenceText) {
      highlightTextReference();
    }
  }, [highlightTextReference, referenceText]);

  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(async () => {
    if (editorChanges) {
      try {
        await navigator.clipboard.writeText(editorChanges);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      } catch (err) {
        console.error("Failed to copy text:", err);
      }
    }
  }, [editorChanges]);

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        width: "100%",
        height: "100%",
        px: 4,
        pt: 2,
        pb: 4,
      }}
    >
      <Box display="flex" justifyContent="flex-end" alignItems="center" mb={2}>
        <Tooltip title={copied ? "Copied!" : "Copy Code"}>
          <IconButton onClick={handleCopy} size="small">
            <CopyIcon size={16} />
          </IconButton>
        </Tooltip>
      </Box>
      <Box
        sx={{
          flex: 1,
          width: "100%",
          minHeight: 0,
        }}
      >
        <Box
          sx={{
            width: "100%",
            height: "100%",
            borderRadius: 2,
            overflow: "hidden",
            border: "1px solid",
            borderColor: "divider",
            "& .monaco-editor": {
              borderRadius: 2,
              overflow: "hidden",
            },
            "& .monaco-editor > .monaco-editor-background": {
              borderRadius: 2,
            },
            "& .monaco-editor .monaco-scrollable-element": {
              borderRadius: 2,
            },
            "& > section": {
              borderRadius: 2,
            },
          }}
        >
          <Editor
            height={"100%"}
            width={"100%"}
            theme={editorTheme}
            className="monaco-editor"
            language="json"
            value={editorChanges}
            beforeMount={handleEditorWillMount}
            onMount={editorDidMount}
            options={editorState.editorOptions}
            onChange={(maybeText) => {
              if (typeof maybeText === "string") {
                handleEditChanges!(maybeText);
              }
            }}
            path={JSON_FILE_NAME}
          />
        </Box>
      </Box>
    </Box>
  );
};

export interface CodeTabProps {
  codeTabActor?: ActorRef<CodeMachineEvents>;
  saveChangesActor?: ActorRef<SaveWorkflowEvents>;
}
export const CodeTab: FunctionComponent<CodeTabProps> = ({
  codeTabActor,
  saveChangesActor,
}) => {
  const { mode } = useContext(ColorModeContext);
  const editorTheme = mode === "dark" ? "vs-dark" : "vs-light";
  return (
    <Box
      style={{
        width: "100%",
        height: "100%",
        position: "relative",
      }}
    >
      <Box
        data-cy="workflow-definition-editor"
        style={{
          width: "100%",
          height: "100%",
          position: "absolute",
        }}
      >
        {codeTabActor && (
          <CodeTabWithActor
            codeTabActor={codeTabActor}
            editorTheme={editorTheme}
          />
        )}
        {saveChangesActor && (
          <ConfirmSaveEditorWithActor
            saveChangesActor={saveChangesActor}
            editorTheme={editorTheme}
          />
        )}
      </Box>
    </Box>
  );
};
