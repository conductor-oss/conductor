import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "pages/definition/state/types";
export declare const usePanelChanges: (actor: ActorRef<WorkflowDefinitionEvents>) => {
    leftPanelExpanded: any;
    setLeftPanelExpanded: () => void;
};
