import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { SaveWorkflowEvents } from "./state";
interface ConfirmSaveButtonGroupProps {
    saveChangesActor: ActorRef<SaveWorkflowEvents>;
}
export declare const ConfirmSaveButtonGroup: FunctionComponent<ConfirmSaveButtonGroupProps>;
export {};
