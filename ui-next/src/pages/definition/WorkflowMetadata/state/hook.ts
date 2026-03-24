import { ActorRef } from "xstate";
import { useSelector } from "@xstate/react";
import { WorkflowMetadataEvents } from "./types";

export const useWorkflowMetadataEditorActor = (
  metadataEditorActor: ActorRef<WorkflowMetadataEvents>,
) => {
  const [nameFieldActor, descriptionFieldActor] = useSelector(
    metadataEditorActor,
    (state) => state.context.editableFieldActors,
  );

  return [
    {
      ownerEmail: useSelector(
        metadataEditorActor,
        (state) => state.context.metadataChanges?.ownerEmail,
      ),
      updateTime: useSelector(
        metadataEditorActor,
        (state) => state.context.metadataChanges?.updateTime,
      ),
      isDisabled: useSelector(metadataEditorActor, (state) =>
        state.matches("editingDisabled"),
      ),
      nameFieldActor,
      descriptionFieldActor,
    },
  ];
};
