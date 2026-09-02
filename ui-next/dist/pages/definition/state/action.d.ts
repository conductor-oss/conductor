import { WorkflowDef } from "types/WorkflowDef";
import { DoneInvokeEvent } from "xstate";
import { WorkflowWithNoErrorsEvent } from "../errorInspector/state";
import { AddNewSwitchTaskEvent, ChangeTabEvent, ChangeVersionEvent, DefinitionMachineContext, DeleteRequestEvent, HandleLeftPanelExpandedEvent, HandleSaveAndCreateNewEvent, HandleSaveAndRunEvent, MoveTaskEvent, PerformOperationEvent, RemoveBranchFromTaskEvent, RemoveTaskEvent, ReplaceTaskEvent, ResetRequestEvent, SaveAndCreateNewRequestEvent, SaveAndRunRequestEvent, SaveAsNewVersionRequestEvent, SyncRunContextAndChangeTabEvent, ToggleAgentExpandedEvent, UpdateAttributesEvent, UpdateWorkflowMetadataEvent, WorkflowFromAgentEvent } from "./types";
import { JsonSchema } from "@jsonforms/core";
import { ImportSummary } from "utils/cloudTemplates";
import { UseLocalCopyChangesEvent } from "../ConfirmLocalCopyDialog/state";
import { SavedSuccessfulEvent } from "../confirmSave/state";
import { HighlightTextReferenceEvent } from "../EditorPanel/CodeEditorTab/state";
export declare const persistWorkflowAttribs: import("xstate").AssignAction<DefinitionMachineContext, UpdateAttributesEvent, UpdateAttributesEvent>;
export declare const updateWf: import("xstate").AssignAction<DefinitionMachineContext, DoneInvokeEvent<{
    workflow: WorkflowDef;
}>, DoneInvokeEvent<{
    workflow: WorkflowDef;
}>>;
export declare const updateWfDefaultRunParam: import("xstate").AssignAction<DefinitionMachineContext, DoneInvokeEvent<{
    schema: JsonSchema;
}>, DoneInvokeEvent<{
    schema: JsonSchema;
}>>;
export declare const updateSecretsAndEnvs: import("xstate").AssignAction<DefinitionMachineContext, DoneInvokeEvent<{
    secrets: Record<string, unknown>[];
    envs: Record<string, unknown>;
}>, DoneInvokeEvent<{
    secrets: Record<string, unknown>[];
    envs: Record<string, unknown>;
}>>;
export declare const resetChanges: import("xstate").AssignAction<DefinitionMachineContext, any, any>;
export declare const updateCollapseWorkflowList: import("xstate").AssignAction<DefinitionMachineContext, any, any>;
export declare const setVersion: import("xstate").AssignAction<DefinitionMachineContext, ChangeVersionEvent, ChangeVersionEvent>;
export declare const resetCurrentVersion: import("xstate").AssignAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const setMessage: import("xstate").AssignAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const processErrorFetching: import("xstate").AssignAction<DefinitionMachineContext, DoneInvokeEvent<{
    message: string;
}>, DoneInvokeEvent<{
    message: string;
}>>;
export declare const resetMessage: import("xstate").AssignAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const notifyFlowUpdates: import("xstate").SendAction<DefinitionMachineContext, any, any>;
export declare const notifyFlowUpdatesFromEvent: import("xstate").SendAction<DefinitionMachineContext, any, any>;
export declare const forwardCollapseWorkflowList: import("xstate").SendAction<DefinitionMachineContext, any, any>;
export declare const notifyFlowResetZoomPosition: import("xstate").SendAction<DefinitionMachineContext, any, any>;
export declare const setFlowAsReadOnly: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
export declare const changeTab: import("xstate").AssignAction<DefinitionMachineContext, ChangeTabEvent | SyncRunContextAndChangeTabEvent, ChangeTabEvent | SyncRunContextAndChangeTabEvent>;
export declare const changeToCodeTab: import("xstate").AssignAction<DefinitionMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const changeToTaskTab: import("xstate").AssignAction<DefinitionMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const changeToPreviousTab: import("xstate").AssignAction<DefinitionMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const performOperation: import("xstate").AssignAction<DefinitionMachineContext, PerformOperationEvent, PerformOperationEvent>;
export declare const replaceTask: import("xstate").AssignAction<DefinitionMachineContext, ReplaceTaskEvent, ReplaceTaskEvent>;
export declare const removeTask: import("xstate").AssignAction<DefinitionMachineContext, RemoveTaskEvent, RemoveTaskEvent>;
export declare const addNewSwitchStatementToTask: import("xstate").AssignAction<DefinitionMachineContext, AddNewSwitchTaskEvent, AddNewSwitchTaskEvent>;
export declare const removeBranchFromTask: import("xstate").AssignAction<DefinitionMachineContext, RemoveBranchFromTaskEvent, RemoveBranchFromTaskEvent>;
export declare const updateWFMetadata: import("xstate").AssignAction<DefinitionMachineContext, UpdateWorkflowMetadataEvent, UpdateWorkflowMetadataEvent>;
export declare const forwardToCodeMachine: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
export declare const forwardToSaveMachine: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
export declare const selectNewTask: import("xstate").SendAction<DefinitionMachineContext, any, any>;
export declare const cleanLastOperation: import("xstate").AssignAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const cleanTaskCrumbSelection: import("xstate").AssignAction<DefinitionMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const updateSelectedCrumbs: import("xstate").AssignAction<DefinitionMachineContext, ReplaceTaskEvent, ReplaceTaskEvent>;
export declare const persistLastOperation: import("xstate").AssignAction<DefinitionMachineContext, PerformOperationEvent, PerformOperationEvent>;
export declare const validateWorkflow: import("xstate").SendAction<DefinitionMachineContext, any, any>;
export declare const forwardCleanWorkflow: import("xstate").SendAction<DefinitionMachineContext, WorkflowWithNoErrorsEvent, any>;
export declare const sendCrumbUpdates: import("xstate").SendAction<DefinitionMachineContext, any, any>;
/**
 * After AI updates, context.workflowChanges is fresh but formTaskMachine still
 * holds stale taskChanges. Send FORCE_REFRESH_TASK (not UPDATE_CRUMBS — that
 * one ignores non-SWITCH/DO_WHILE tasks via maybeUseChanges) so the form
 * immediately reflects the agent's changes. Read from event.workflow to avoid
 * XState v4 assign/sendTo ordering issues (assign updates context for the next
 * snapshot, so sendTo in the same transition still sees the old context).
 */
export declare const syncTaskFormWithAgentWorkflow: import("xstate").ChooseAction<DefinitionMachineContext, WorkflowFromAgentEvent, WorkflowFromAgentEvent>;
export declare const persistSelectedTabCrumbs: import("xstate").AssignAction<DefinitionMachineContext, any, any>;
export declare const forwardToErrorInspector: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
export declare const forwardSelectEdge: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
export declare const logStuff: (context: DefinitionMachineContext, event: any) => void;
export declare const startRenderingGtag: (context: DefinitionMachineContext, event: any) => void;
export declare const gtagEventLogger: (context: DefinitionMachineContext, event: any) => void;
export declare const gtagErrorLogger: (context: DefinitionMachineContext, event: any) => void;
export declare const cleanServerErrors: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
export declare const cleanRunErrors: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
export declare const persistWorkflowChanges: import("xstate").AssignAction<DefinitionMachineContext, any, any>;
export declare const sendWorkflowToInspector: import("xstate").SendAction<DefinitionMachineContext, any, any>;
export declare const sendWorkflowChangesToMetadataMachine: import("xstate").SendAction<DefinitionMachineContext, any, any>;
export declare const sendWorkflowChangesToMetadataMachineFromEvent: import("xstate").ChooseAction<DefinitionMachineContext, any, any>;
export declare const forwardWorkflowToCodeMachine: import("xstate").SendAction<DefinitionMachineContext, any, any>;
export declare const notifyToFlowIfOutputParameters: import("xstate").ChooseAction<DefinitionMachineContext, UpdateWorkflowMetadataEvent, UpdateWorkflowMetadataEvent>;
export declare const persistRemovalOperation: import("xstate").AssignAction<DefinitionMachineContext, RemoveBranchFromTaskEvent, RemoveBranchFromTaskEvent>;
export declare const cleanLastRemovalOperation: import("xstate").AssignAction<DefinitionMachineContext, any, any>;
export declare const applyLastRemovalOperationAsRemoveTaskOperation: import("xstate").AssignAction<DefinitionMachineContext, any, any>;
export declare const forwardWorkflowToLocalCopyMachine: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
export declare const forwardWorkflowToMetadataEditorMachine: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
export declare const forwardWorkflowToTabMetadataEditorMachine: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
export declare const removeLocalCopy: import("xstate").RaiseAction<DefinitionMachineContext, any, any>;
export declare const persistWorkflowNameAndVersion: import("xstate").AssignAction<DefinitionMachineContext, SavedSuccessfulEvent, SavedSuccessfulEvent>;
export declare const maybePersistLocalCopyMessage: import("xstate").AssignAction<DefinitionMachineContext, DoneInvokeEvent<{
    workflow: Partial<WorkflowDef>;
    isLocalStorageEmpty?: boolean;
}>, DoneInvokeEvent<{
    workflow: Partial<WorkflowDef>;
    isLocalStorageEmpty?: boolean;
}>>;
export declare const moveTaskFromLocation: import("xstate").AssignAction<DefinitionMachineContext, MoveTaskEvent, MoveTaskEvent>;
export declare const selectMovedTask: import("xstate").SendAction<DefinitionMachineContext, any, any>;
export declare const reSelectTaskIfSelected: import("xstate").PureAction<DefinitionMachineContext, any, any>;
export declare const cleanLocalCopyMessage: import("xstate").AssignAction<DefinitionMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const updateWfFromLocalStorage: import("xstate").AssignAction<DefinitionMachineContext, DoneInvokeEvent<UseLocalCopyChangesEvent | {
    workflow?: Partial<WorkflowDef>;
}>, DoneInvokeEvent<UseLocalCopyChangesEvent | {
    workflow?: Partial<WorkflowDef>;
}>>;
export declare const fireChangeToWorkflowTab: import("xstate").RaiseAction<DefinitionMachineContext, any, any>;
export declare const fireChangeToCodeTab: import("xstate").RaiseAction<DefinitionMachineContext, any, any>;
export declare const fireChangeToRunTab: import("xstate").RaiseAction<DefinitionMachineContext, any, any>;
export declare const fireChangeToDependenciesTab: import("xstate").RaiseAction<DefinitionMachineContext, any, any>;
export declare const handleLeftPanelExpanded: import("xstate").RaiseAction<DefinitionMachineContext, HandleLeftPanelExpandedEvent, HandleLeftPanelExpandedEvent>;
export declare const persistCodeReference: import("xstate").AssignAction<DefinitionMachineContext, HighlightTextReferenceEvent, HighlightTextReferenceEvent>;
export declare const cleanCodeTextReference: import("xstate").AssignAction<DefinitionMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const setRunTabAsPreviousTab: import("xstate").AssignAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const fireSaveEvent: import("xstate").RaiseAction<DefinitionMachineContext, SaveAndRunRequestEvent, SaveAndRunRequestEvent>;
export declare const fireSaveAndCreateNewRequestEvent: import("xstate").RaiseAction<DefinitionMachineContext, SaveAndCreateNewRequestEvent, SaveAndCreateNewRequestEvent>;
export declare const raiseResetEvent: import("xstate").RaiseAction<DefinitionMachineContext, ResetRequestEvent, ResetRequestEvent>;
export declare const raiseDeleteEvent: import("xstate").RaiseAction<DefinitionMachineContext, DeleteRequestEvent, DeleteRequestEvent>;
export declare const raiseSaveEvent: import("xstate").RaiseAction<DefinitionMachineContext, SaveAsNewVersionRequestEvent, SaveAsNewVersionRequestEvent>;
export declare const raiseSaveAndRunEvent: import("xstate").RaiseAction<DefinitionMachineContext, HandleSaveAndRunEvent, HandleSaveAndRunEvent>;
export declare const justExecute: import("xstate").SendAction<DefinitionMachineContext, any, any>;
export declare const raiseSaveAndCreateNewEvent: import("xstate").RaiseAction<DefinitionMachineContext, HandleSaveAndCreateNewEvent, HandleSaveAndCreateNewEvent>;
export declare const maybeSelectInitialSelectedTaskReference: import("xstate").PureAction<DefinitionMachineContext, any, any>;
export declare const cleanInitialSelectedTaskReferenceName: import("xstate").AssignAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const setSaveSourceEventType: import("xstate").AssignAction<DefinitionMachineContext, HandleSaveAndCreateNewEvent, HandleSaveAndCreateNewEvent>;
export declare const raiseUpdateAtribsEvent: import("xstate").RaiseAction<DefinitionMachineContext, UpdateAttributesEvent, UpdateAttributesEvent>;
export declare const persistWorkflowVersionsParsed: import("xstate").AssignAction<DefinitionMachineContext, DoneInvokeEvent<{
    versions: number[];
}>, DoneInvokeEvent<{
    versions: number[];
}>>;
export declare const persistLatestVersion: import("xstate").AssignAction<DefinitionMachineContext, DoneInvokeEvent<{
    versions: string[];
}>, DoneInvokeEvent<{
    versions: string[];
}>>;
export declare const markDontShowImportSuccessfulDialogAgain: () => void;
export declare const showTaskDescriptions: import("xstate").SendAction<DefinitionMachineContext, any, any>;
export declare const persistImportSummary: import("xstate").AssignAction<DefinitionMachineContext, DoneInvokeEvent<ImportSummary>, DoneInvokeEvent<ImportSummary>>;
export declare const reportErrorToErrorInspector: import("xstate").SendAction<unknown, DoneInvokeEvent<{
    message: string;
}>, any>;
export declare const cleanSerializationError: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
export declare const updateRunTabFormState: import("xstate").AssignAction<DefinitionMachineContext, SyncRunContextAndChangeTabEvent, SyncRunContextAndChangeTabEvent>;
export declare const toggleAgentExpanded: import("xstate").AssignAction<DefinitionMachineContext, ToggleAgentExpandedEvent, ToggleAgentExpandedEvent>;
export declare const collapseAgent: import("xstate").AssignAction<DefinitionMachineContext, any, any>;
export declare const autoExpandAgentForNewWorkflow: import("xstate").AssignAction<DefinitionMachineContext, any, any>;
export declare const forwardToRunWorkflowMachine: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
