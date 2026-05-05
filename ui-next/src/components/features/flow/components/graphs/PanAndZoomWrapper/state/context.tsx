import { createContext, ReactNode } from "react";
import { ActorRef } from "xstate";
import { PanAndZoomEvents } from "./types";

export interface PanAndZoomContextProps {
  panAndZoomActor?: ActorRef<PanAndZoomEvents>;
  children?: ReactNode;
}

export const PanAndZoomContext = createContext<PanAndZoomContextProps>({
  panAndZoomActor: undefined,
});
