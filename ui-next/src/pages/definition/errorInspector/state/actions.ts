import { assign, raise, sendParent, DoneInvokeEvent, choose } from "xstate";
import { respond } from "xstate/lib/actions";
import _isEmpty from "lodash/isEmpty";

import { adjust, remove } from "utils";
import {
  ValidateWorkflowStringEvent,
  ErrorInspectorMachineContext,
  ErrorInspectorEventTypes,
  WorkflowWithNoErrorsEvent,
  WorkflowHasErrorsEvent,
  ValidateSingleTaskEvent,
  FlowReportedErrorEvent,
  ReportServerErrorEvent,
  ValidationError,
  ErrorIds,
  ErrorTypes,
  ErrorSeverity,
  CleanServerErrorsEvent,
  ValidateWorkflowEvent,
  ReferenceProblems,
  FlowFinishedRenderingEvent,
  SetWorkflowEvent,
  UpdateSecretsEvent,
  ToggleClickReference,
  SetErrorInspectorExpandedEvent,
  ToggleErrorInspectorEvent,
  SetErrorInspectorCollapsedEvent,
  ReportRunErrorEvent,
  CollapseInspectorIfNoErrorsEvent,
  TaskErrors,
} from "./types";
import { CodeMachineEventTypes } from "pages/definition/EditorPanel/CodeEditorTab/state";
import {
  computeWorkflowStringErrors,
  computeWorkflowErrors,
  findTaskError,
} from "./schemaValidator";
import { TaskDef } from "types";
import {
  filterServerErrorsNotPresentInNodes,
  nodesToCrumbMap,
  reverifyServerErrorsTaskChanges,
  serverValidationErrorToIndexTask,
} from "./helpers";
import { SaveWorkflowMachineEventTypes } from "pages/definition/confirmSave/state/types";

export const testForTaskErrors = assign<
  ErrorInspectorMachineContext,
  ValidateSingleTaskEvent
>(({ taskErrors }, { task }) => {
  const ntaskErrors = findTaskError(task); // TODO error checker should check if taskReferenceName exists
  const taskIndex = taskErrors.findIndex(
    ({ task: { taskReferenceName } }) =>
      taskReferenceName === task?.taskReferenceName,
  );
  if (_isEmpty(ntaskErrors)) {
    // no errors. remove entry if exists
    return {
      taskErrors:
        taskIndex === -1 ? taskErrors : remove(taskIndex, 1, taskErrors),
    };
  }
  // Errors. report the new findings
  return {
    taskErrors:
      taskIndex === -1
        ? taskErrors.concat({ task, errors: ntaskErrors })
        : adjust<{ task: TaskDef; errors: ValidationError[] }>(
            taskIndex,
            () => ({
              task,
              errors: ntaskErrors,
            }),
            taskErrors,
          ),
  };
});

export const respondTaskErrors = respond<
  ErrorInspectorMachineContext,
  ValidateSingleTaskEvent
>(({ taskErrors }) => {
  return {
    type: ErrorInspectorEventTypes.SINGLE_TASK_ERRORS,
    taskErrors,
  };
});

export const testForErrors = assign<
  ErrorInspectorMachineContext,
  ValidateWorkflowEvent
>((_context, { workflow }) => ({
  currentWf: workflow,
  ...computeWorkflowErrors(workflow),
}));

export const verifyChangesInServerErrors = assign<
  ErrorInspectorMachineContext,
  ValidateWorkflowEvent
>((context, { workflow }) => {
  const { serverErrors } = context;
  const updatedServerErrors = reverifyServerErrorsTaskChanges(
    serverErrors,
    workflow,
  );
  return {
    serverErrors: updatedServerErrors ?? [],
  };
});

export const testForErrorsInStringWorkflow = assign<
  ErrorInspectorMachineContext,
  ValidateWorkflowStringEvent
>(({ serverErrors }, { workflowChanges }) => {
  const maybeErrors = computeWorkflowStringErrors(workflowChanges);
  if (maybeErrors.currentWf != null) {
    const updatedServerErrors = reverifyServerErrorsTaskChanges(
      serverErrors,
      maybeErrors.currentWf,
    );
    return {
      ...maybeErrors,
      serverErrors: updatedServerErrors ?? [],
    };
  }
  return {
    ...maybeErrors,
  };
});

export const notifyErrorFree = sendParent<
  ErrorInspectorMachineContext,
  WorkflowWithNoErrorsEvent
>(({ currentWf }) => ({
  type: ErrorInspectorEventTypes.WORKFLOW_WITH_NO_ERRORS,
  workflow: currentWf,
}));

export const workflowHasErrors = sendParent<
  ErrorInspectorMachineContext,
  WorkflowHasErrorsEvent
>(({ taskErrors, workflowErrors, currentWf }) => ({
  type: ErrorInspectorEventTypes.WORKFLOW_HAS_ERRORS,
  errors: {
    taskErrors,
    workflowErrors,
  },
  workflow: currentWf,
}));

export const flowErrorToWorkflowError = assign<
  ErrorInspectorMachineContext,
  FlowReportedErrorEvent
>({
  workflowErrors: ({ workflowErrors }, { text }) => {
    const flowError: ValidationError = {
      id: ErrorIds.FLOW_ERROR,
      message: text,
      hint: "Assert taskReferenceName is not repeated across tasks",
      type: ErrorTypes.WORKFLOW,
      severity: ErrorSeverity.ERROR,
    };
    return workflowErrors.concat(flowError);
  },
});

export const removeServerErrorsRelatedToRemovedTasks = assign<
  ErrorInspectorMachineContext,
  FlowFinishedRenderingEvent
>(({ serverErrors }, { nodes }) => {
  const updatedServerErrors = filterServerErrorsNotPresentInNodes(
    serverErrors,
    nodes,
  );
  return {
    serverErrors: updatedServerErrors ?? [],
  };
});

export const persistServerError = assign<
  ErrorInspectorMachineContext,
  ReportServerErrorEvent
>(({ currentWf }, { text, validationErrors: incomingValidationErrors }) => {
  const validationErrors = incomingValidationErrors ?? [
    {
      path: "workflow",
      message: text,
    },
  ]; // Server error reported without validation. will be treated as a workflow error
  const serverError: ValidationError = {
    id: ErrorIds.FLOW_ERROR,
    message: text,
    hint: "Assert taskReferenceName is not repeated across tasks",
    type: ErrorTypes.WORKFLOW,
    severity: ErrorSeverity.ERROR,
    validationErrors:
      validationErrors == null
        ? undefined
        : serverValidationErrorToIndexTask(
            validationErrors || [],
            currentWf?.tasks || [],
          ),
  };

  return {
    serverErrors: [serverError],
  };
});

export const persistRunError = assign<
  ErrorInspectorMachineContext,
  ReportRunErrorEvent
>((_, { text }) => {
  const runError: ValidationError = {
    id: ErrorIds.FLOW_ERROR,
    message: text,
    hint: "Check run parameters",
    type: ErrorTypes.RUN_ERROR,
    severity: ErrorSeverity.ERROR,
  };

  return {
    runWorkflowErrors: [runError],
  };
});

export const persistCrumbMap = assign<
  ErrorInspectorMachineContext,
  FlowFinishedRenderingEvent
>((_context, { nodes }) => {
  return {
    crumbMap: nodesToCrumbMap(nodes),
    /* currentWf: workflow, */
  };
});

export const persistReferenceProblems = assign<
  ErrorInspectorMachineContext,
  DoneInvokeEvent<ReferenceProblems>
>((_, event) => {
  const { data } = event;
  return {
    lastRemovedTask: undefined,
    lastTaskCrumbs: [],
    workflowReferenceProblems: data.workflowReferenceProblems,
    taskReferencesProblems: data.taskReferencesProblems,
    unreachableTaskProblems: data.unreachableTaskProblems,
  };
});

export const cleanRunError = assign<
  ErrorInspectorMachineContext,
  CleanServerErrorsEvent
>({
  runWorkflowErrors: () => [],
});

export const cleanServerErrors = assign<
  ErrorInspectorMachineContext,
  CleanServerErrorsEvent
>({
  serverErrors: () => [],
  runWorkflowErrors: () => [],
});

export const persistCurrentWorkflow = assign<
  ErrorInspectorMachineContext,
  SetWorkflowEvent
>({
  currentWf: (__, { workflow }) => workflow,
});

export const updateSecretEnvs = assign<
  ErrorInspectorMachineContext,
  UpdateSecretsEvent
>((_, event) => {
  const { data } = event;
  return {
    secrets: data?.secrets,
    envs: data?.envs,
  };
});

export const sendReferenceText = sendParent<
  ErrorInspectorMachineContext,
  ToggleClickReference
>((_, event) => {
  return {
    type: CodeMachineEventTypes.HIGHLIGHT_TEXT_REFERENCE,
    reference: {
      textReference: event.referenceText,
      referenceReason: "error",
    },
  };
});

export const sendJumpToFirstError = sendParent<
  ErrorInspectorMachineContext,
  ToggleClickReference
>(() => {
  return {
    type: CodeMachineEventTypes.JUMP_TO_FIRST_ERROR,
  };
});

export const toggleErrorInspector = assign<
  ErrorInspectorMachineContext,
  ToggleErrorInspectorEvent
>({
  expanded: (context) => !context.expanded,
});

export const setErrorInspectorExpanded = assign<
  ErrorInspectorMachineContext,
  SetErrorInspectorExpandedEvent
>({
  expanded: () => true,
});

export const setErrorInspectorCollapsed = assign<
  ErrorInspectorMachineContext,
  SetErrorInspectorCollapsedEvent
>({
  expanded: () => false,
});

export const sendCancelConfirmSave = sendParent<
  ErrorInspectorMachineContext,
  ToggleClickReference
>(() => {
  return {
    type: SaveWorkflowMachineEventTypes.CANCEL_SAVE_EVT,
  };
});

export const raiseExpandErrorInspector = raise<
  ErrorInspectorMachineContext,
  any
>(() => {
  return {
    type: ErrorInspectorEventTypes.SET_ERROR_INSPECTOR_EXPANDED,
  };
});

export const raiseCollapseErrorInspector = raise<
  ErrorInspectorMachineContext,
  any
>(() => {
  return {
    type: ErrorInspectorEventTypes.SET_ERROR_INSPECTOR_COLLAPSED,
  };
});

export const raiseCollapseErrorInspectorIfNoErrors = raise<
  ErrorInspectorMachineContext,
  any
>(() => {
  return {
    type: ErrorInspectorEventTypes.COLLAPSE_INSPECTOR_IF_NO_ERRORS,
  };
});

export const cleanSerializationError = assign<ErrorInspectorMachineContext>({
  workflowErrors: (context) => {
    return context.workflowErrors.filter(
      (error) => error.id !== ErrorIds.SERIALIZATION_ERROR,
    );
  },
});

export const collapseInspectorIfNoErrors = choose<
  ErrorInspectorMachineContext,
  CollapseInspectorIfNoErrorsEvent
>([
  {
    cond: ({
      workflowReferenceProblems,
      taskReferencesProblems,
      unreachableTaskProblems,
    }: ErrorInspectorMachineContext) => {
      const taskTotalErrors = taskReferencesProblems.reduce(
        (acc: number, { errors }: TaskErrors) => acc + errors.length,
        0,
      );
      return (
        workflowReferenceProblems.length +
          unreachableTaskProblems.length +
          taskTotalErrors ===
        0
      );
    },
    actions: [raiseCollapseErrorInspector],
  },
]);
