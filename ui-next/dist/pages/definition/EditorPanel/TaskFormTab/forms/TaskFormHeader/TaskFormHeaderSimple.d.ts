import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { TaskHeaderMachineEvents } from "./state/types";
export interface TaskFormHeaderSimpleProps {
    taskFormHeaderActor: ActorRef<TaskHeaderMachineEvents>;
}
export declare const TaskFormHeaderSimple: FunctionComponent<TaskFormHeaderSimpleProps>;
