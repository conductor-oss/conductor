import { ReactNode } from "react";
import { ActorRef } from "xstate";
import { MetdataFieldMachineEvents } from "./state";
interface ChildrenProps {
    onChange: (value: any) => void;
    value: any;
    someKey: string;
}
export interface ActorToHandlerValueProps {
    children: (props: ChildrenProps) => ReactNode;
    actor: ActorRef<MetdataFieldMachineEvents>;
}
export declare const ActorToHandlerValue: ({ actor, children, }: ActorToHandlerValueProps) => ReactNode;
export {};
