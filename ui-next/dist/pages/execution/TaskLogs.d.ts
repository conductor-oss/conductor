import { ActorRef } from "xstate";
import { RightPanelEvents } from "./RightPanel/state";
export interface TaskLogsProps {
    containerQueryState: any;
    rightPanelActor: ActorRef<RightPanelEvents>;
}
export default function TaskLogs({ containerQueryState, rightPanelActor, }: TaskLogsProps): import("react").JSX.Element;
