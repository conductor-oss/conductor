import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { SaveWorkflowEvents } from "./state";
interface ConfirmWorkflowOverrideProps {
    saveChangesActor: ActorRef<SaveWorkflowEvents>;
}
export declare const ConfirmWorkflowOverride: FunctionComponent<ConfirmWorkflowOverrideProps>;
export {};
