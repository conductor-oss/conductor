import { createContext } from "react";
import { QueueMonitorContextProps } from "./types";

export const QueueMonitorContext = createContext<QueueMonitorContextProps>({
  queueMachineActor: undefined,
});
