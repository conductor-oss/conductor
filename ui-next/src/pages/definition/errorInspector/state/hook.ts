import { ActorRef } from "xstate";
import { useSelector } from "@xstate/react";
import {
  ErrorInspectorMachineEvents,
  ErrorInspectorEventTypes,
  TaskErrors,
} from "./types";

export const useErrorInspectorActor = (
  errorInspectorActor: ActorRef<ErrorInspectorMachineEvents>,
) => {
  const send = errorInspectorActor.send;

  const handleToggleTaskErrors = () => {
    send({
      type: ErrorInspectorEventTypes.TOGGLE_TASK_ERRORS_VIEWER,
    });
  };

  const handleToggleWorkflowErrors = () => {
    send({
      type: ErrorInspectorEventTypes.TOGGLE_WORKFLOW_ERRORS_VIEWER,
    });
  };

  const handleClickReference = (referenceText: string) => {
    send({
      type: ErrorInspectorEventTypes.CLICK_REFERENCE,
      referenceText,
    });
  };

  const handleJumpToFirstError = () => {
    send({
      type: ErrorInspectorEventTypes.JUMP_TO_FIRST_ERROR,
    });
  };

  const handleToggleTaskReferenceErrors = () => {
    send({
      type: ErrorInspectorEventTypes.TOGGLE_TASK_REFERENCE_ERRORS_VIEWER,
    });
  };

  const handleToggleWorkflowReferenceErrors = () => {
    send({
      type: ErrorInspectorEventTypes.TOGGLE_WORKFLOW_REFERENCE_ERRORS_VIEWER,
    });
  };

  const handleCleanServerErrors = () => {
    send({
      type: ErrorInspectorEventTypes.CLEAN_SERVER_ERRORS,
    });
  };

  const handleToggleErrorInspector = () => {
    send({
      type: ErrorInspectorEventTypes.TOGGLE_ERROR_INSPECTOR,
    });
  };

  const handleSetErrorInspectorCollapsed = () => {
    send({
      type: ErrorInspectorEventTypes.SET_ERROR_INSPECTOR_COLLAPSED,
    });
  };

  return [
    {
      workflowErrors: useSelector(
        errorInspectorActor,
        (state) => state.context.workflowErrors,
      ),
      taskErrors: useSelector(
        errorInspectorActor,
        (state) => state.context.taskErrors,
      ),
      unreachableTaskErrors: useSelector(
        errorInspectorActor,
        (state) => state.context.unreachableTaskProblems,
      ),
      serverErrors: useSelector(
        errorInspectorActor,
        (state) => state.context.serverErrors,
      ),
      runWorkflowErrors: useSelector(
        errorInspectorActor,
        (state) => state.context.runWorkflowErrors,
      ),
      taskReferenceErrors: useSelector(
        errorInspectorActor,
        (state) => state.context.taskReferencesProblems,
      ),
      workflowReferenceErrors: useSelector(
        errorInspectorActor,
        (state) => state.context.workflowReferenceProblems,
      ),
      errorCount: useSelector(
        errorInspectorActor,
        ({
          context: {
            workflowErrors = [],
            taskErrors = [],
            serverErrors = [],
            runWorkflowErrors = [],
          },
        }) => {
          const taskTotalErrors = taskErrors.reduce(
            (acc: number, { errors }: TaskErrors) => acc + errors.length,
            0,
          );
          return (
            workflowErrors.length +
            taskTotalErrors +
            serverErrors.length +
            runWorkflowErrors.length
          );
        },
      ),
      warningCount: useSelector(
        errorInspectorActor,
        ({
          context: {
            workflowReferenceProblems,
            taskReferencesProblems,
            unreachableTaskProblems,
          },
        }) => {
          const taskTotalErrors = taskReferencesProblems.reduce(
            (acc: number, { errors }: TaskErrors) => acc + errors.length,
            0,
          );
          return (
            workflowReferenceProblems.length +
            unreachableTaskProblems.length +
            taskTotalErrors
          );
        },
      ),
      taskErrorsExpanded: useSelector(errorInspectorActor, (state) =>
        state.matches(
          "errorsDisplay.controlledErrors.withErrors.taskErrorsViewer.expanded",
        ),
      ),
      workflowErrorsExpanded: useSelector(errorInspectorActor, (state) =>
        state.matches(
          "errorsDisplay.controlledErrors.withErrors.workflowErrorsViewer.expanded",
        ),
      ),
      referenceTaskErrorsExpanded: useSelector(errorInspectorActor, (state) =>
        state.matches(
          "errorsDisplay.missingReferences.referencesMenus.taskReferences.expanded",
        ),
      ),
      referenceWorkflowErrorsExpanded: useSelector(
        errorInspectorActor,
        (state) =>
          state.matches(
            "errorsDisplay.missingReferences.referencesMenus.workflowReferences.expanded",
          ),
      ),
      expanded: useSelector(
        errorInspectorActor,
        (state) => state.context.expanded,
      ),
      tasks: useSelector(
        errorInspectorActor,
        (state) => state.context.currentWf?.tasks,
      ),
    },
    {
      handleToggleTaskErrors,
      handleToggleWorkflowErrors,
      handleCleanServerErrors,
      handleToggleTaskReferenceErrors,
      handleToggleWorkflowReferenceErrors,
      handleClickReference,
      handleToggleErrorInspector,
      handleSetErrorInspectorCollapsed,
      handleJumpToFirstError,
    },
  ] as const;
};
