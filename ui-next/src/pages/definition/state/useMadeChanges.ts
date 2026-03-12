import { ActorRef } from "xstate";
import { useMemo } from "react";
import { WorkflowDefinitionEvents } from "./types";
import { useSelector } from "@xstate/react";
import fastDeepEqual from "fast-deep-equal";
/*
Use this hook as low as the state tree can go. it is subscribed to workflowChanges
*/
export const useWorkflowChanges = (
  service: ActorRef<WorkflowDefinitionEvents>,
) => {
  const isNewWorkflow = useSelector(
    service,
    (state) => state.context.isNewWorkflow,
  );
  const currentWf = useSelector(service, (state) => state.context.currentWf);

  const workflowChanges = useSelector(
    service,
    (state) => state.context.workflowChanges,
  );

  const madeChanges = useMemo(() => {
    return isNewWorkflow ? true : !fastDeepEqual(workflowChanges, currentWf);
  }, [workflowChanges, currentWf, isNewWorkflow]);

  return {
    isNewWorkflow,
    currentWf,
    workflowChanges,
    madeChanges,
  };
};
