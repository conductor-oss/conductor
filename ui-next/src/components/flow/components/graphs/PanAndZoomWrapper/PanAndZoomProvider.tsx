import { ReactNode } from "react";
import { ActorRef } from "xstate";
import { PanAndZoomContext, PanAndZoomEvents } from "./state";

export interface PanAndZoomContextProps {
  panAndZoomActor?: ActorRef<PanAndZoomEvents>;
  children?: ReactNode;
}

const PanAndZoomContextProvider = ({
  children,
  panAndZoomActor,
}: PanAndZoomContextProps) => (
  <PanAndZoomContext.Provider value={{ panAndZoomActor }}>
    {children}
  </PanAndZoomContext.Provider>
);

export default PanAndZoomContextProvider;
