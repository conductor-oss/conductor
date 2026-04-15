import { FunctionComponent } from "react";
import { QueueMonitorContext } from "./QueueMonitorContext";
import { QueueMonitorContextProps } from "./types";

export const QueueMonitorContextProvider: FunctionComponent<
  QueueMonitorContextProps
> = ({ children, queueMachineActor }) => (
  <QueueMonitorContext.Provider value={{ queueMachineActor }}>
    {children}
  </QueueMonitorContext.Provider>
);
