import { ActorRef } from "xstate";
interface QuickAddMenuProps {
    anchorEl: HTMLElement | null;
    richAddTaskMenuActor: ActorRef<any>;
}
declare const QuickAddMenu: ({ anchorEl, richAddTaskMenuActor, }: QuickAddMenuProps) => import("react").JSX.Element;
export default QuickAddMenu;
