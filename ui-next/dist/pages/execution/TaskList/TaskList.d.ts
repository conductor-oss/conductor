import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { TaskListMachineEvents } from "./state";
export declare const MIN_DATE_WIDTH = "175px";
interface TaskListProps {
    taskListActor: ActorRef<TaskListMachineEvents>;
    executionAlert: string;
}
export declare const TaskList: FunctionComponent<TaskListProps>;
export {};
