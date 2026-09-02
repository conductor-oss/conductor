import { ReactNode } from "react";
import { ActorRef } from "xstate";
import { PanAndZoomEvents } from "./state";
export interface PanAndZoomContextProps {
    panAndZoomActor?: ActorRef<PanAndZoomEvents>;
    children?: ReactNode;
}
declare const PanAndZoomContextProvider: ({ children, panAndZoomActor, }: PanAndZoomContextProps) => import("react").JSX.Element;
export default PanAndZoomContextProvider;
