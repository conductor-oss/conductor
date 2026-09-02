import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { FlowEvents } from "./state";
import "./ReaflowOverrides.scss";
interface FlowProps {
    flowActor: ActorRef<FlowEvents>;
    readOnly?: boolean;
    leftPanelExpanded: boolean;
    isExecutionView?: boolean;
}
export declare const Flow: FunctionComponent<FlowProps>;
export {};
