import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { QueueMonitorMachineEvents } from "../state";
export interface FilterSectionProps {
    queueMachineActor: ActorRef<QueueMonitorMachineEvents>;
}
export declare const FilterSection: FunctionComponent<FilterSectionProps>;
