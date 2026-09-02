import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { LocalCopyMachineEvents } from "./state";
interface ConfirmLocalCopyDialogProps {
    localCopyActor: ActorRef<LocalCopyMachineEvents>;
}
export declare const ConfirmLocalCopyDialog: FunctionComponent<ConfirmLocalCopyDialogProps>;
export {};
