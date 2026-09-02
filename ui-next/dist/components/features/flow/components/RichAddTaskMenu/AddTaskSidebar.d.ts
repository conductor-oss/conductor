import React from "react";
import { ActorRef } from "xstate";
import { RichAddTaskMenuEvents } from "./state/types";
type AddTaskSidebarProps = {
    open: boolean;
    setOpen?: (val: boolean) => void;
    richAddTaskMenuActor: ActorRef<RichAddTaskMenuEvents>;
};
declare const AddTaskSidebar: ({ open, setOpen, richAddTaskMenuActor, }: AddTaskSidebarProps) => React.JSX.Element | null;
export default AddTaskSidebar;
