import { useSelector } from "@xstate/react";
import { useMemo } from "react";
import { crumbsToTaskSteps } from "components/features/flow/nodes";
import _initial from "lodash/initial";
import { extractVariablesFromTask } from "../helpers";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "./types";
import { definitionAsDrawn } from "pages/execution/state/codeLoops";

export const useGetVariablesForSelectedTasks = (
  workflowDefinitionActor: ActorRef<WorkflowDefinitionEvents> | undefined,
) => {
  const selectedTaskCrumbs = useSelector(
    workflowDefinitionActor!,
    (state) => state.context.selectedTaskCrumbs,
  );

  // the definition as drawn: a native one has loop nodes the selected crumbs go through
  const workflowChanges = useSelector(
    workflowDefinitionActor!,
    (state) => state.context.workflowChanges,
  );
  const editorTasks = useMemo(
    () => definitionAsDrawn(workflowChanges ?? {})?.tasks ?? [],
    [workflowChanges],
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
