import { SelectNodeEvent } from "components/features/flow/state";
import { DefinitionMachineContext, ChangeTabEvent, PerformOperationEvent, SaveAndRunRequestEvent, SaveRequestEvent } from "./types";
import { WorkflowWithNoErrorsEvent } from "../errorInspector/state";
import { DoneInvokeEvent } from "xstate";
export declare const isNewWorkflow: (context: DefinitionMachineContext) => boolean;
export declare const isWorkflowNotFound: (_context: DefinitionMachineContext, event: DoneInvokeEvent<{
    message?: string;
    status?: number;
}>) => boolean;
export declare const isEditorTab: ({ openedTab }: DefinitionMachineContext) => boolean;
export declare const isRunTab: ({ openedTab }: DefinitionMachineContext) => boolean;
export declare const isDependenciesTab: ({ openedTab }: DefinitionMachineContext) => boolean;
export declare const comesFromCodeAimsTaskTabHasSelectedTask: ({ openedTab, selectedTaskCrumbs }: DefinitionMachineContext, { tab }: ChangeTabEvent) => boolean;
export declare const isTaskEditorTab: ({ openedTab }: DefinitionMachineContext) => boolean;
export declare const isWorkflowEditorTab: ({ openedTab }: DefinitionMachineContext) => boolean;
export declare const isDifferentTab: ({ openedTab }: DefinitionMachineContext, { tab }: ChangeTabEvent) => boolean;
export declare const isValidSelection: (_context: DefinitionMachineContext, { node: { id } }: SelectNodeEvent) => boolean;
export declare const isChangingTab: ({ openedTab }: DefinitionMachineContext, { tab }: ChangeTabEvent) => boolean;
export declare const hasLastPerformedOperation: ({ lastPerformedOperation, }: DefinitionMachineContext) => boolean;
export declare const wasSaved: (_context: DefinitionMachineContext, event: DoneInvokeEvent<{
    saved: boolean;
}>) => boolean;
export declare const workflowWasSentWithNoErrors: (__context: DefinitionMachineContext, event: WorkflowWithNoErrorsEvent) => boolean;
export declare const hasSelectedTask: ({ selectedTaskCrumbs, }: DefinitionMachineContext) => boolean;
export declare const wantToRemoveLastForkIndex: ({ lastRemovalOperation, }: DefinitionMachineContext) => boolean;
export declare const isAddOperation: (__context: DefinitionMachineContext, { data }: PerformOperationEvent) => boolean;
export declare const isLastVersion: (context: DefinitionMachineContext, event: DoneInvokeEvent<{
    versions: string[];
}>) => boolean;
export declare const selectedTaskIsInForkBranch: ({ lastRemovalOperation, selectedTaskCrumbs, }: DefinitionMachineContext) => boolean;
export declare const selectedTaskIsInSwitchBranch: ({ lastRemovalOperation, selectedTaskCrumbs, }: DefinitionMachineContext) => boolean;
export declare const isDescriptionEmpty: (context: DefinitionMachineContext, event: SaveRequestEvent) => boolean;
export declare const isSaveAndRunRequest: (__context: DefinitionMachineContext, { isSaveAndRun }: SaveAndRunRequestEvent) => boolean;
export declare const hasNoChanges: (context: DefinitionMachineContext) => boolean;
export declare const isSaveAndRunWithNoChanges: (context: DefinitionMachineContext, event: SaveAndRunRequestEvent) => boolean;
export declare const isFirstTimeFlow: (context: DefinitionMachineContext) => boolean;
export declare const dontNeedToShowImportSuccessfulDialog: (context: DefinitionMachineContext) => boolean;
export declare const importSummaryHasDependencies: (context: DefinitionMachineContext) => boolean;
