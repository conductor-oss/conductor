export declare const useEventHandlerDefinition: () => readonly [{
    readonly handleDeleteRequest: () => import("xstate").State<import("./types").SaveEventHandlerMachineContext, import("./types").SaveEventHandlerEvents, any, {
        value: any;
        context: import("./types").SaveEventHandlerMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").SaveEventHandlerEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly handleConfirmDelete: () => import("xstate").State<import("./types").SaveEventHandlerMachineContext, import("./types").SaveEventHandlerEvents, any, {
        value: any;
        context: import("./types").SaveEventHandlerMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").SaveEventHandlerEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly handleConfirmReset: () => import("xstate").State<import("./types").SaveEventHandlerMachineContext, import("./types").SaveEventHandlerEvents, any, {
        value: any;
        context: import("./types").SaveEventHandlerMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").SaveEventHandlerEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly handleResetRequest: () => import("xstate").State<import("./types").SaveEventHandlerMachineContext, import("./types").SaveEventHandlerEvents, any, {
        value: any;
        context: import("./types").SaveEventHandlerMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").SaveEventHandlerEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly handleCancelRequest: () => import("xstate").State<import("./types").SaveEventHandlerMachineContext, import("./types").SaveEventHandlerEvents, any, {
        value: any;
        context: import("./types").SaveEventHandlerMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").SaveEventHandlerEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly handleConfirmSaveRequest: () => import("xstate").State<import("./types").SaveEventHandlerMachineContext, import("./types").SaveEventHandlerEvents, any, {
        value: any;
        context: import("./types").SaveEventHandlerMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").SaveEventHandlerEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly handleSaveRequest: () => import("xstate").State<import("./types").SaveEventHandlerMachineContext, import("./types").SaveEventHandlerEvents, any, {
        value: any;
        context: import("./types").SaveEventHandlerMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").SaveEventHandlerEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly handleEditChanges: (changes: string) => import("xstate").State<import("./types").SaveEventHandlerMachineContext, import("./types").SaveEventHandlerEvents, any, {
        value: any;
        context: import("./types").SaveEventHandlerMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").SaveEventHandlerEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
    readonly handleDefineNewEventHandler: () => void;
    readonly handleConfirmNewEventHandler: () => void;
    readonly handleBackToIdle: () => void;
    readonly handleClearErrorMessage: () => void;
    readonly toggleFormMode: () => void;
    readonly service: import("xstate").Interpreter<import("./types").SaveEventHandlerMachineContext, any, import("./types").SaveEventHandlerEvents, {
        value: any;
        context: import("./types").SaveEventHandlerMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").SaveEventHandlerEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
}, {
    readonly isNewEventHandler: boolean;
    readonly eventHandlerName: string;
    readonly originalSource: string;
    readonly editorChanges: string;
    readonly isConfirmReset: boolean;
    readonly isConfirmDelete: boolean;
    readonly isConfirmNew: boolean;
    readonly madeChanges: boolean;
    readonly isUpdatingToNewChanges: boolean;
    readonly isConfirmSave: boolean;
    readonly isSaving: boolean;
    readonly isIdle: boolean;
    readonly message: string;
    readonly isFormMode: boolean;
    readonly isEditorMode: boolean;
    readonly couldNotParseJson: boolean;
    readonly isFetching: boolean;
}];
