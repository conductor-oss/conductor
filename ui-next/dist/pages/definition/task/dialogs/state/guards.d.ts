import { HandleDefineNewConfirmationEvent, HandleDeleteTaskDefConfirmationEvent, HandleResetConfirmationEvent, TaskDefinitionDialogsContext } from "pages/definition/task/dialogs/state/types";
export declare const isConfirm: (_: TaskDefinitionDialogsContext, event: HandleDefineNewConfirmationEvent | HandleDeleteTaskDefConfirmationEvent | HandleResetConfirmationEvent) => boolean;
