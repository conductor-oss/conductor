import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { TaskDefinitionMachineEvent } from "./state";
export interface SaveProtectionPromptProps {
    taskDefActor: ActorRef<TaskDefinitionMachineEvent>;
}
export declare const SaveProtectionPrompt: FunctionComponent<SaveProtectionPromptProps>;
