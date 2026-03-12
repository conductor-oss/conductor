import { ReactNode } from "react";
import { ActorRef } from "xstate";
import { TaskFormEvents } from "../types";

export interface TaskFormContextProviderProps {
  formTaskActor?: ActorRef<TaskFormEvents>;
  children?: ReactNode;
}
