import { ActorRef } from "xstate";
import { TaskDefinitionDialogsMachineEvent } from "pages/definition/task/dialogs/state/types";
export declare const useTaskDefinitionDialogs: (actor: ActorRef<TaskDefinitionDialogsMachineEvent>) => readonly [{
    readonly confirmationDialogDefineNewOpen: any;
    readonly confirmationDialogDeleteOpen: any;
    readonly confirmationDialogResetOpen: any;
    readonly modifiedTaskDefinition: any;
}, {
    readonly handleDefineNewConfirmation: (isConfirm: boolean) => void;
    readonly handleDeleteTaskDefConfirmation: (isConfirm: boolean) => void;
    readonly handleResetConfirmation: (isConfirm: boolean) => void;
}];
