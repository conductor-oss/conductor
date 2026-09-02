import { RichAddTaskMenuMachineContext } from "./types";
export declare const fetchForTaskDefinitions: ({ authHeaders: headers, }: RichAddTaskMenuMachineContext) => Promise<any>;
export declare const fetchForWorkflowDefinitions: ({ authHeaders: headers, }: RichAddTaskMenuMachineContext) => Promise<any>;
export declare const fetchForMCPIntegrations: ({ authHeaders: headers, }: RichAddTaskMenuMachineContext) => Promise<{
    supportedIntegrations: any;
    availableIntegrations: any;
}>;
export declare const fetchForIntegrationTools: ({ authHeaders: headers, integrationDrillDownMenu: { selectedIntegration }, }: RichAddTaskMenuMachineContext) => Promise<any>;
