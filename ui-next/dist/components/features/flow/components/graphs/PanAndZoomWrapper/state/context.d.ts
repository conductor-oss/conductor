import { ReactNode } from "react";
import { ActorRef } from "xstate";
import { PanAndZoomEvents } from "./types";
export interface PanAndZoomContextProps {
    panAndZoomActor?: ActorRef<PanAndZoomEvents>;
    children?: ReactNode;
}
export declare const PanAndZoomContext: import("react").Context<PanAndZoomContextProps>;
