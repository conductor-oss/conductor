import { FunctionComponent } from "react";
import { DoWhileSelection } from "types/Execution";
import { ActorRef } from "xstate";
import { RightPanelEvents } from "./state";
export interface RightPanelProps {
    rightPanelActor: ActorRef<RightPanelEvents>;
    workflowName: string;
    workflowStatus: string;
    doWhileSelection?: DoWhileSelection[];
}
export declare const RightPanel: FunctionComponent<RightPanelProps>;
