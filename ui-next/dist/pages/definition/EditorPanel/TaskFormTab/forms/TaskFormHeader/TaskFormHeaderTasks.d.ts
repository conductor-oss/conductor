import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { TaskHeaderMachineEvents } from "./state/types";
export interface TaskFormHeaderTasksProps {
    taskFormHeaderActor: ActorRef<TaskHeaderMachineEvents>;
}
export declare const TaskFormHeaderTasks: FunctionComponent<TaskFormHeaderTasksProps>;
