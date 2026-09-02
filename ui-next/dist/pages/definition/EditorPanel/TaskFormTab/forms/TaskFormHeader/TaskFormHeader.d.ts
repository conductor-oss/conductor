import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { TaskHeaderMachineEvents } from "./state/types";
export interface TaskFormHeaderProps {
    taskFormHeaderActor: ActorRef<TaskHeaderMachineEvents>;
}
declare const TaskFormHeader: FunctionComponent<TaskFormHeaderProps>;
export default TaskFormHeader;
