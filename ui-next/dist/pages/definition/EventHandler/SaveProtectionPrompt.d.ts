import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { SaveEventHandlerEvents } from "./eventhandlers/state";
export interface SaveProtectionPromptProps {
    service: ActorRef<SaveEventHandlerEvents>;
}
export declare const SaveProtectionPrompt: FunctionComponent<SaveProtectionPromptProps>;
