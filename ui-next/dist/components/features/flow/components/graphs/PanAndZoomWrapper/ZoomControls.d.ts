import { FlowEvents } from "components/features/flow/state";
import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
export interface ZoomControlsProps {
    zoom: number;
    setZoom: (zoomIn: boolean) => void;
    resetPosition: () => void;
    isInconsistent: boolean;
    fitToScreen: () => void;
    togglePan: () => void;
    panEnabled: boolean;
    flowActor: ActorRef<FlowEvents>;
    isSearchFieldVisible: boolean;
    toggleSearchField: () => void;
    printScreen: () => void;
    isExecutionView: boolean;
}
export declare const ZoomControls: FunctionComponent<ZoomControlsProps>;
