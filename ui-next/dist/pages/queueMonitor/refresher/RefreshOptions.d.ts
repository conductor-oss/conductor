import { ReactNode } from "react";
import { ActorRef } from "xstate";
import { TimerEvents } from "./state";
interface RefreshOptionsPresentationalProps {
    onRefresh: () => void;
    timerActor: ActorRef<TimerEvents>;
    startIcon: ReactNode;
}
export declare const RefreshButton: ({ onRefresh, timerActor, startIcon, }: RefreshOptionsPresentationalProps) => import("react").JSX.Element;
export declare const RefreshOptions: () => import("react").JSX.Element;
export {};
