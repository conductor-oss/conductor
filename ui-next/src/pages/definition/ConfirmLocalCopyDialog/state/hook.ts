import { ActorRef } from "xstate";
import { LocalCopyMachineEvents, LocalCopyMachineEventTypes } from "./types";
export const useLocalCopyMachine = (
  service: ActorRef<LocalCopyMachineEvents>,
) => {
  const handleRemoveLocalCopyMessage = () =>
    service.send({
      type: LocalCopyMachineEventTypes.REMOVE_LOCAL_COPY_MESSAGE,
    });
  return [
    {
      handleRemoveLocalCopyMessage,
    },
  ];
};
