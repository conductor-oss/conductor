import { useSelector } from "@xstate/react";
import { useMemo } from "react";
import { crumbsToTaskSteps } from "components/flow/nodes";
import _initial from "lodash/initial";
import { extractVariablesFromTask } from "../helpers";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "./types";

export const useGetVariablesForSelectedTasks = (
  workflowDefinitionActor: ActorRef<WorkflowDefinitionEvents> | undefined,
) => {
  const selectedTaskCrumbs = useSelector(
    workflowDefinitionActor!,
    (state) => state.context.selectedTaskCrumbs,
  );

  const editorTasks = useSelector(
    workflowDefinitionActor!,
    (state) => state.context.workflowChanges.tasks,
  );

  const tasksInCrumbBranch = useMemo(() => {
    if (editorTasks.length > 0) {
      return _initial(crumbsToTaskSteps(selectedTaskCrumbs, editorTasks));
    }
    return [];
  }, [editorTasks, selectedTaskCrumbs]);

  const variableInputs = extractVariablesFromTask(tasksInCrumbBranch);
  return variableInputs;
};
