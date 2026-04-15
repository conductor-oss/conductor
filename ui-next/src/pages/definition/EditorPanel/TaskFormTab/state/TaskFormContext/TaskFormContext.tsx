import { createContext } from "react";
import { TaskFormContextProviderProps } from "./types";

export const TaskFormContext = createContext<TaskFormContextProviderProps>({
  formTaskActor: undefined,
});
