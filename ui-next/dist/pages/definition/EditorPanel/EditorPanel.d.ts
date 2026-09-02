import React from "react";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "../state/types";
interface EditorPanelProps {
    definitionActor: ActorRef<WorkflowDefinitionEvents>;
}
declare const EditorPanel: ({ definitionActor }: EditorPanelProps) => React.JSX.Element;
export default EditorPanel;
