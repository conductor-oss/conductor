import { FlowEvents } from "components/features/flow/state";
import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
interface SearchBoxProps {
    flowActor: ActorRef<FlowEvents>;
    anchorEl: any;
}
export declare const SearchBox: FunctionComponent<SearchBoxProps>;
export {};
