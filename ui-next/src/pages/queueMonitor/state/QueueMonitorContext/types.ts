import { ReactNode } from "react";
import { ActorRef } from "xstate";
import { QueueMonitorMachineEvents } from "../types";

export interface QueueMonitorContextProps {
  queueMachineActor?: ActorRef<QueueMonitorMachineEvents>;
  children?: ReactNode;
}
