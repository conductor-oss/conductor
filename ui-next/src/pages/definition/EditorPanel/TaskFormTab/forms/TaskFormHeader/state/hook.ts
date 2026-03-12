import { ActorRef } from "xstate";
import { useSelector } from "@xstate/react";
import { WorkflowMetadataEvents } from "pages/definition/WorkflowMetadata/state";

export const useWorkflowMetadataEditorActor = (
  metadataEditorActor: ActorRef<WorkflowMetadataEvents>,
) => {
  const [
    inputParametersActor,
    outputParametersActors,
    restartableActors,
    timeoutSecondsActors,
    timeoutPolicyActors,
    failureWorkflowActors,
  ] = useSelector(
    metadataEditorActor,
    (state) => state.context.editableFieldActors,
  );

  const isReady = useSelector(metadataEditorActor, (state) =>
    state.hasTag("editingEnabled"),
  );

  return [
    {
      inputParametersActor,
      outputParametersActors,
      restartableActors,
      timeoutSecondsActors,
      timeoutPolicyActors,
      failureWorkflowActors,
      isReady,
    },
  ];
};
