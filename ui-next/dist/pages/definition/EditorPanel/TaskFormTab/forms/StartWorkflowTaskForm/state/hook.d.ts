import { ActorRef } from "xstate";
import { StartSubWfNameVersionEvents } from "./types";
export declare const useStartSubWfNameVersionMachine: (actor: ActorRef<StartSubWfNameVersionEvents>) => readonly [{
    readonly wfNameOptions: any;
    readonly availableVersions: any;
    readonly isFetching: any;
}, {
    readonly handleSelectWorkflowName: (name: string) => void;
}];
