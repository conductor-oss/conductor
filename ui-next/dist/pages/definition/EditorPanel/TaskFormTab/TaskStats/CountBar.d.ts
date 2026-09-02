import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { TaskStatsEvents } from "./state";
interface CountBarProps {
    taskStatsActor: ActorRef<TaskStatsEvents>;
}
export declare const CountBar: FunctionComponent<CountBarProps>;
export {};
