import { ActorRef, State } from "xstate";
import { useSelector } from "@xstate/react";
import {
  CodeMachineEvents,
  CodeMachineEventTypes,
  CodeMachineContext,
} from "./types";

export const useCodeTabActor = (actor: ActorRef<CodeMachineEvents>) => {
  const handleEditChanges = (changes: string) => {
    actor.send({
      type: CodeMachineEventTypes.EDIT_EVT,
      changes,
    });
  };
  return [
    {
      editorChanges: useSelector(actor, (state) => state.context.editorChanges),
      referenceText: useSelector(
        actor,
        (state: State<CodeMachineContext>) => state.context.referenceText,
      ),
      shouldTakeToFirstError: useSelector(
        actor,
        (state: State<CodeMachineContext>) => state.hasTag("showFirstError"),
      ),
    },
    {
      handleEditChanges,
    },
  ] as const;
};
