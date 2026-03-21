import { ActorRef } from "xstate";
import { useSelector } from "@xstate/react";
import {
  StartSubWfNameVersionEvents,
  StartSubWfNameVersionStates,
  StartSubWfNameVersionTypes,
} from "./types";

export const useStartSubWfNameVersionMachine = (
  actor: ActorRef<StartSubWfNameVersionEvents>,
) => {
  const wfNameOptions = useSelector(
    actor,
    (state) => state.context.wfNameOptions,
  );

  const availableVersions = useSelector(
    actor,
    (state) => state.context.availableVersions,
  );

  const isFetching = useSelector(
    actor,
    (state) =>
      state.matches(StartSubWfNameVersionStates.HANDLE_SELECT_WORKFLOW_NAME) ||
      state.matches(StartSubWfNameVersionStates.GO_BACK_TO_IDLE),
  );

  const handleSelectWorkflowName = (name: string) => {
    actor.send({
      type: StartSubWfNameVersionTypes.SELECT_WORKFLOW_NAME,
      name,
    });
  };

  return [
    {
      wfNameOptions,
      availableVersions,
      isFetching,
    },
    {
      handleSelectWorkflowName,
    },
  ] as const;
};
