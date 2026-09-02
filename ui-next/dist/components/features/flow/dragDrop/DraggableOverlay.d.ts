import { FlowEvents } from "components/features/flow/state";
import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
export interface DragOverlayProps {
    flowActor: ActorRef<FlowEvents>;
}
export declare const DraggableOverlay: FunctionComponent<DragOverlayProps>;
