import { createContext, ReactNode } from "react";
import { ActorRef } from "xstate";
import { FlowEvents } from "./types";

export interface FlowContextProps {
  flowActor?: ActorRef<FlowEvents>;
  children?: ReactNode;
}

export const FlowActorContext = createContext<FlowContextProps>({
  flowActor: undefined,
});
