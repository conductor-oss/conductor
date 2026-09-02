import { QueueMonitorMachineEvents } from "./types";
import { ActorRef } from "xstate";
export declare const useQueueMachine: () => ActorRef<QueueMonitorMachineEvents>;
