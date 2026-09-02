import { ReactNode } from "react";
import { ActorRef } from "xstate";
import { FlowEvents } from "./types";
export interface FlowContextProps {
    flowActor?: ActorRef<FlowEvents>;
    children?: ReactNode;
}
export declare const FlowActorContext: import("react").Context<FlowContextProps>;
