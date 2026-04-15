import {
  WORKFLOW_TAB,
  TASK_TAB,
  CODE_TAB,
  RUN_TAB,
  DEPENDENCIES_TAB,
} from "./constants";
import {
  START_TASK_FAKE_TASK_REFERENCE_NAME,
  END_TASK_FAKE_TASK_REFERENCE_NAME,
} from "components/features/flow/nodes";
import { SelectNodeEvent } from "components/features/flow/state";

import _isNil from "lodash/isNil";
import _last from "lodash/last";
import {
  DefinitionMachineContext,
  ChangeTabEvent,
  PerformOperationEvent,
  SaveAndRunRequestEvent,
  DONT_SHOW_IMPORT_SUCCESSFUL_DIALOG_TUTORIAL_AGAIN,
} from "./types";
import { WorkflowWithNoErrorsEvent } from "../errorInspector/state";
import { DoneInvokeEvent } from "xstate";
import { ADD_TASK, ADD_TASK_ABOVE, ADD_TASK_BELOW } from "./taskModifier";
import { TaskType } from "types";
import fastDeepEqual from "fast-deep-equal";
import { queryClient } from "queryClient";
import { fetchContextNonHook } from "plugins/fetch";
import { WORKFLOW_METADATA_BASE_URL_SHORT } from "utils/constants/api";

const fetchContext = fetchContextNonHook();

export const isNewWorkflow = (context: DefinitionMachineContext) =>
  context.isNewWorkflow;

export const isEditorTab = ({ openedTab }: DefinitionMachineContext) =>
  openedTab === CODE_TAB;

export const isRunTab = ({ openedTab }: DefinitionMachineContext) =>
  openedTab === RUN_TAB;

export const isDependenciesTab = ({ openedTab }: DefinitionMachineContext) =>
  openedTab === DEPENDENCIES_TAB;

export const comesFromCodeAimsTaskTabHasSelectedTask = (
  { openedTab, selectedTaskCrumbs }: DefinitionMachineContext,
  { tab }: ChangeTabEvent,
) =>
  openedTab === CODE_TAB && tab === TASK_TAB && selectedTaskCrumbs?.length > 0;

export const isTaskEditorTab = ({ openedTab }: DefinitionMachineContext) =>
  openedTab === TASK_TAB;

export const isWorkflowEditorTab = ({ openedTab }: DefinitionMachineContext) =>
  openedTab === WORKFLOW_TAB;

export const isDifferentTab = (
  { openedTab }: DefinitionMachineContext,
  { tab }: ChangeTabEvent,
) => openedTab !== tab;

export const isValidSelection = (
  _context: DefinitionMachineContext,
  { node: { id } }: SelectNodeEvent,
) =>
  ![
    START_TASK_FAKE_TASK_REFERENCE_NAME,
    END_TASK_FAKE_TASK_REFERENCE_NAME,
  ].includes(id);

export const isChangingTab = (
  { openedTab }: DefinitionMachineContext,
  { tab }: ChangeTabEvent,
) => openedTab !== tab;

export const hasLastPerformedOperation = ({
  lastPerformedOperation,
}: DefinitionMachineContext) => !_isNil(lastPerformedOperation);

export const wasSaved = (
  _context: DefinitionMachineContext,
  event: DoneInvokeEvent<{ saved: boolean }>,
) => event.data.saved;

export const workflowWasSentWithNoErrors = (
  __context: DefinitionMachineContext,
  event: WorkflowWithNoErrorsEvent,
) => event.workflow !== undefined;

export const hasSelectedTask = ({
  selectedTaskCrumbs,
}: DefinitionMachineContext) => selectedTaskCrumbs.length === 0;

export const wantToRemoveLastForkIndex = ({
  lastRemovalOperation,
}: DefinitionMachineContext) => {
  return (
    lastRemovalOperation?.task.type === TaskType.FORK_JOIN &&
    lastRemovalOperation?.task?.forkTasks?.length === 1 &&
    lastRemovalOperation?.branchName === "0"
  );
};

export const isAddOperation = (
  __context: DefinitionMachineContext,
  { data }: PerformOperationEvent,
) => {
  return [ADD_TASK, ADD_TASK_ABOVE, ADD_TASK_BELOW].includes(data?.action);
};

export const isLastVersion = (
  context: DefinitionMachineContext,
  event: DoneInvokeEvent<{ versions: string[] }>,
) => {
  if (event.data.versions?.length === 0) {
    return true;
  }
  return false;
};

export const selectedTaskIsInForkBranch = ({
  lastRemovalOperation,
  selectedTaskCrumbs,
}: DefinitionMachineContext) => {
  if (lastRemovalOperation?.task?.type === TaskType.FORK_JOIN) {
    const lastCrumb = _last(selectedTaskCrumbs);
    if (lastCrumb != null) {
      return lastRemovalOperation.branchName === String(lastCrumb.forkIndex);
    }
  }
  return false;
};

export const selectedTaskIsInSwitchBranch = ({
  lastRemovalOperation,
  selectedTaskCrumbs,
}: DefinitionMachineContext) => {
  if (lastRemovalOperation?.task?.type === TaskType.SWITCH) {
    const lastCrumb = _last(selectedTaskCrumbs);
    if (lastCrumb != null) {
      return lastRemovalOperation.branchName === lastCrumb.decisionBranch;
    }
  }
  return false;
};

export const isDescriptionEmpty = (context: DefinitionMachineContext) =>
  (context.workflowChanges?.description ?? "").trim() === "";

export const isSaveAndRunRequest = (
  __context: DefinitionMachineContext,
  { isSaveAndRun }: SaveAndRunRequestEvent,
) => {
  return isSaveAndRun ? true : false;
};

export const hasNoChanges = (context: DefinitionMachineContext) =>
  fastDeepEqual(context.workflowChanges, context.currentWf);

export const isSaveAndRunWithNoChanges = (
  context: DefinitionMachineContext,
  event: SaveAndRunRequestEvent,
) => {
  return isSaveAndRunRequest(context, event) && hasNoChanges(context);
};

export const isFirstTimeFlow = (context: DefinitionMachineContext) => {
  const workflowDefinitionUrl = WORKFLOW_METADATA_BASE_URL_SHORT;
  const key = [fetchContext.stack, workflowDefinitionUrl];
  const data = queryClient.getQueryData<any>(key);
  // Check by local storage. and if there is any workflow in cache
  return (
    Boolean(context.successfullyImportedWorkflowId) === true &&
    (data === undefined || data?.length === 0)
  );
};

export const dontNeedToShowImportSuccessfulDialog = (
  context: DefinitionMachineContext,
) => {
  const dontShowImportSuccessfulDialogTutorialAgain = localStorage.getItem(
    DONT_SHOW_IMPORT_SUCCESSFUL_DIALOG_TUTORIAL_AGAIN,
  );
  return (
    Boolean(context.successfullyImportedWorkflowId) === false ||
    dontShowImportSuccessfulDialogTutorialAgain === "true"
  );
};

export const importSummaryHasDependencies = (
  context: DefinitionMachineContext,
) => {
  return (
    context.importSummary?.integrationsAndModelsResponse != null &&
    context.importSummary?.integrationsAndModelsResponse.length > 0
  );
};
