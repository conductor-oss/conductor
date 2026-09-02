import { ActorRef } from "xstate";
import { IntegrationDrillDownMenuProp, IntegrationMenuItem, RichAddTaskMenuEvents } from "./types";
import { NodeData } from "reaflow";
export declare const useRichAddTaskMenu: (richAddTaskMenuActor: ActorRef<RichAddTaskMenuEvents>) => readonly [{
    readonly menuType: any;
    readonly supportedIntegrations: any;
    readonly availableIntegrations: any;
    readonly integrationDefs: any;
    readonly integrationTypes: any;
    readonly integrationDrillDownMenu: any;
    readonly scrollPosition: any;
    readonly operationContext: any;
    readonly nodes: NodeData<any>[];
    readonly workerMenuItems: any;
    readonly subWorkflowMenuItems: any;
    readonly selectedTab: any;
    readonly isFetching: any;
    readonly isFetchingIntegrationTools: any;
    readonly searchQuery: any;
}, {
    readonly handleChangeMenuType: (menuType: "quick" | "advanced") => void;
    readonly handleFetchIntegrationTools: (integration: IntegrationMenuItem) => void;
    readonly refetchIntegrations: () => void;
    readonly handleUpdateIntegrationDrillDown: (integration: IntegrationDrillDownMenuProp) => void;
    readonly handleTyping: (value: any) => void;
}];
