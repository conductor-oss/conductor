import { FunctionComponent, useRef } from "react";
import { Box } from "@mui/material";
import { ActorRef } from "xstate";
import { DiffOnMount, MonacoDiffEditor } from "@monaco-editor/react";
import { useActor, useSelector } from "@xstate/react";
import { SaveWorkflowEvents, SaveWorkflowMachineEventTypes } from "./state";
import { DiffEditor } from "components/DiffEditor/DiffEditor";

interface ConfirmSaveDiffEditorProps {
  saveChangesActor: ActorRef<SaveWorkflowEvents>;
  editorTheme: string;
  editorState: {
    editorOptions: Record<string, unknown>;
  };
}

export const ConfirmSaveDiffEditor: FunctionComponent<
  ConfirmSaveDiffEditorProps
> = ({ saveChangesActor, editorTheme, editorState }) => {
  const diffMonacoObjects = useRef<MonacoDiffEditor | null>(null);
  const [, send] = useActor(saveChangesActor);

  const handleEditChanges = (changes: string) =>
    send({ type: SaveWorkflowMachineEventTypes.EDIT_DEBOUNCE_EVT, changes });

  const isNewWorkflow = useSelector(
    saveChangesActor,
    (state) => state.context.isNewWorkflow,
  );
  const editorChanges = useSelector(
    saveChangesActor,
    (state) => state.context.editorChanges,
  );
  const oldWorkflow = useSelector(saveChangesActor, (state) =>
    JSON.stringify(state.context.currentWf, null, 2),
  );

  const diffEditorDidMount: DiffOnMount = (editor) => {
    diffMonacoObjects.current = editor;
    const modifiedEditor = editor.getModifiedEditor();
    modifiedEditor.onDidChangeModelContent((_: any) => {
      const maybeText = modifiedEditor.getValue();
      if (typeof maybeText === "string") {
        handleEditChanges(maybeText);
      }
    });
  };

  return (
    <Box
      data-cy="workflow-definition-diff-editor"
      style={{
        position: "absolute",
        top: 0,
        left: 0,
        height: "100%",
        width: "100%",
        display: "block",
      }}
    >
      <DiffEditor
        height={"100%"}
        width={"100%"}
        theme={editorTheme}
        language="json"
        original={isNewWorkflow ? "" : oldWorkflow}
        modified={editorChanges}
        onMount={diffEditorDidMount}
        options={editorState.editorOptions}
      />
    </Box>
  );
};
