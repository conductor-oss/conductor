import { TaskDef, Crumb } from "types";
import { ActorRef } from "xstate";
import {
  WorkflowDefinitionEvents,
  DefinitionMachineEventTypes,
  OperationContext,
  PerformedOperation,
} from "./types";

export type TaskAndCrumbs = {
  task: TaskDef;
  crumbs: Crumb[];
};

export const usePerformOperationOnDefinition = (
  service: ActorRef<WorkflowDefinitionEvents>,
) => {
  const handleReplaceTask = (
    { task, crumbs }: TaskAndCrumbs,
    newTask: TaskDef,
  ) => {
    service.send({
      type: DefinitionMachineEventTypes.REPLACE_TASK_EVT,
      task,
      crumbs,
      newTask,
    });
  };

  const handleRemoveTask = ({ task, crumbs }: TaskAndCrumbs) => {
    service.send({
      type: DefinitionMachineEventTypes.REMOVE_TASK_EVT,
      task,
      crumbs,
    });
  };

  const handleAddSwitchPath = ({ task, crumbs }: TaskAndCrumbs) => {
    service.send({
      type: DefinitionMachineEventTypes.ADD_NEW_SWITCH_PATH_EVT,
      task,
      crumbs,
    });
  };

  const handlePerformOperation = (operationData: {
    data: OperationContext;
    operation: PerformedOperation;
  }) => {
    service.send({
      type: DefinitionMachineEventTypes.PERFORM_OPERATION_EVT,
      ...operationData,
    });
  };

  const handleRemoveBranch = (
    removeBranchRelevantData: TaskAndCrumbs & { branchName: string },
  ) => {
    service.send({
      type: DefinitionMachineEventTypes.REMOVE_BRANCH_EVT,
      ...removeBranchRelevantData,
    });
  };
  return {
    handleReplaceTask,
    handleRemoveTask,
    handleAddSwitchPath,
    handleRemoveBranch,
    handlePerformOperation,
  };
};
