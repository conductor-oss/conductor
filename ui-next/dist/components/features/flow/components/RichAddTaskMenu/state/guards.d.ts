import { RichAddTaskMenuMachineContext, SetSelectedTabEvent } from "./types";
export declare const isTabIsWorkers: (_context: RichAddTaskMenuMachineContext, { tab }: SetSelectedTabEvent) => boolean;
export declare const isTabIsSubWorkflows: (_context: RichAddTaskMenuMachineContext, { tab }: SetSelectedTabEvent) => boolean;
export declare const isTaskDefNotFetched: ({ isTaskDefFetched, }: RichAddTaskMenuMachineContext) => boolean;
export declare const isSubWfNotFetched: ({ isSubWfFetched, }: RichAddTaskMenuMachineContext) => boolean;
export declare const isIntegrationsNotFetched: ({ isIntegrationsFetched, }: RichAddTaskMenuMachineContext) => boolean;
export declare const isTabIsIntegrations: (_context: RichAddTaskMenuMachineContext, { tab }: SetSelectedTabEvent) => boolean;
