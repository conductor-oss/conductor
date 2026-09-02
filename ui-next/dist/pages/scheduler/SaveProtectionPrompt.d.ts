import { FunctionComponent } from "react";
import { ActorRef, AnyEventObject } from "xstate";
export interface SaveProtectionPromptProps {
    isInFormView: number;
    data: Record<string, unknown>;
    initialFormData: Record<string, unknown>;
    changedCodeData: Record<string, unknown>;
    actor?: ActorRef<AnyEventObject>;
    isSaveInProgress?: boolean;
    hasErrors?: boolean;
    onSave?: () => void;
}
export declare const SaveProtectionPrompt: FunctionComponent<SaveProtectionPromptProps>;
