import { ActorRef } from "xstate";
interface ConfirmationDialogsProps {
    isConfirmReset: boolean;
    isConfirmDelete: boolean;
    isConfirmingForkRemoval: boolean;
    isSaveRequest: boolean;
    localCopyActor: ActorRef<any> | undefined;
    saveChangesActor: ActorRef<any> | undefined;
    onResetConfirmation: (val: boolean) => void;
    onDeleteConfirmation: (val: boolean) => void;
    onCancelRequest: () => void;
    onConfirmLastForkRemovalRequest: () => void;
}
export declare const ConfirmationDialogs: ({ isConfirmReset, isConfirmDelete, isConfirmingForkRemoval, isSaveRequest, localCopyActor, saveChangesActor, onResetConfirmation, onDeleteConfirmation, onCancelRequest, onConfirmLastForkRemovalRequest, }: ConfirmationDialogsProps) => import("react").JSX.Element;
export {};
