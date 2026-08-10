import { FunctionComponent } from "react";
import { TaskFormContext } from "./TaskFormContext";
import { TaskFormContextProviderProps } from "./types";

export const TaskFormContextProvider: FunctionComponent<
  TaskFormContextProviderProps
> = ({ children, formTaskActor }) => (
  <TaskFormContext.Provider value={{ formTaskActor }}>
    {children}
  </TaskFormContext.Provider>
);
