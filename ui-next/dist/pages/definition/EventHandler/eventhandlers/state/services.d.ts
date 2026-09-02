import { SaveEventHandlerMachineContext } from "./types";
export declare const createEventHandler: ({ editorChanges, authHeaders }: SaveEventHandlerMachineContext, __: any) => Promise<any>;
export declare const updateEventHandler: ({ editorChanges, authHeaders }: SaveEventHandlerMachineContext, __: any) => Promise<any>;
export declare const fetchEventHandler: ({ authHeaders, eventHandlerName, isNewEventHandler, }: SaveEventHandlerMachineContext, __: any) => Promise<any>;
export declare const deleteEventHandler: ({ eventHandlerName, authHeaders }: SaveEventHandlerMachineContext, __: any) => Promise<any>;
