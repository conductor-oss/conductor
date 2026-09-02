import { ActorRef } from "xstate";
import { LocalCopyMachineEvents } from "./types";
export declare const useLocalCopyMachine: (service: ActorRef<LocalCopyMachineEvents>) => {
    handleRemoveLocalCopyMessage: () => void;
}[];
