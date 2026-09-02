import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { SaveWorkflowEvents } from "../../confirmSave/state/types";
import "./MonacoDefinitionOverrides.scss";
import { CodeMachineEvents } from "./state/types";
export interface CodeTabProps {
    codeTabActor?: ActorRef<CodeMachineEvents>;
    saveChangesActor?: ActorRef<SaveWorkflowEvents>;
}
export declare const CodeTab: FunctionComponent<CodeTabProps>;
