import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { SaveWorkflowEvents } from "./state";
interface ConfirmSaveDiffEditorProps {
    saveChangesActor: ActorRef<SaveWorkflowEvents>;
    editorTheme: string;
    editorState: {
        editorOptions: Record<string, unknown>;
    };
}
export declare const ConfirmSaveDiffEditor: FunctionComponent<ConfirmSaveDiffEditorProps>;
export {};
