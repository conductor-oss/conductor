import { DoneInvokeEvent } from "xstate";
import { SaveEventHandlerMachineEventTypes, SaveEventHandlerMachineContext, EditEvent, EditDebounceEvent, UpdateEventHandlerEvent, UpdateOriginalSourceEvent, ShowErrorMessageEvent, ClearErrorMessageEvent } from "./types";
export declare const editChanges: import("xstate").AssignAction<SaveEventHandlerMachineContext, EditEvent, EditEvent>;
export declare const debounceEditEvent: import("xstate").SendAction<SaveEventHandlerMachineContext, EditDebounceEvent, import("xstate").AnyEventObject>;
export declare const cancelDebounceEditChanges: import("xstate").CancelAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const updateEventHandlerName: import("xstate").AssignAction<SaveEventHandlerMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const updateEventHandler: import("xstate").AssignAction<SaveEventHandlerMachineContext, UpdateEventHandlerEvent, UpdateEventHandlerEvent>;
export declare const updateOriginalSource: import("xstate").AssignAction<SaveEventHandlerMachineContext, UpdateOriginalSourceEvent, UpdateOriginalSourceEvent>;
export declare const revertToOriginalSource: import("xstate").AssignAction<SaveEventHandlerMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const resetToNewDefinition: import("xstate").AssignAction<SaveEventHandlerMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const showErrorMessage: import("xstate").AssignAction<SaveEventHandlerMachineContext, ShowErrorMessageEvent, ShowErrorMessageEvent>;
export declare const clearErrorMessage: import("xstate").AssignAction<SaveEventHandlerMachineContext, ClearErrorMessageEvent, ClearErrorMessageEvent>;
export declare const forwardEventToFormMachine: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
export declare const persistFormChanges: import("xstate").AssignAction<SaveEventHandlerMachineContext, DoneInvokeEvent<{
    eventAsJson: {
        name?: string;
        event?: string;
        evaluatorType?: string;
        condition?: string;
        actions?: [];
    };
    reason: SaveEventHandlerMachineEventTypes;
}>, DoneInvokeEvent<{
    eventAsJson: {
        name?: string;
        event?: string;
        evaluatorType?: string;
        condition?: string;
        actions?: [];
    };
    reason: SaveEventHandlerMachineEventTypes;
}>>;
export declare const persistIsNewEventHandler: import("xstate").AssignAction<SaveEventHandlerMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const sendSavedSuccessful: import("xstate").SendAction<SaveEventHandlerMachineContext, any, import("xstate").AnyEventObject>;
export declare const sendSavedCancelled: import("xstate").SendAction<SaveEventHandlerMachineContext, any, import("xstate").AnyEventObject>;
