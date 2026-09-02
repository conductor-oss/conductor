import { FlowEvents } from "components/features/flow/state";
import { FunctionComponent, ReactNode } from "react";
import { ActorRef } from "xstate";
import { PanAndZoomEvents } from "./state";
interface PanAndZoomWrapperProps {
    isInconsistent: boolean;
    panAndZoomActor: ActorRef<PanAndZoomEvents>;
    leftPanelExpanded: boolean;
    viewPortChildren?: ReactNode;
    children: ReactNode;
    flowActor: ActorRef<FlowEvents>;
    isExecutionView?: boolean;
}
declare const PanAndZoomWrapper: FunctionComponent<PanAndZoomWrapperProps>;
export default PanAndZoomWrapper;
