import { RightPanelContext } from "./types";
export declare const isSelectedTaskStatusUpdatable: ({ selectedTask, taskDetails, }: RightPanelContext) => boolean | undefined;
export declare const isSummaryTab: ({ currentTab }: RightPanelContext) => boolean;
export declare const isInputTab: ({ currentTab }: RightPanelContext) => boolean;
export declare const isOutputTab: ({ currentTab }: RightPanelContext) => boolean;
export declare const isAgentCardTab: ({ currentTab }: RightPanelContext) => boolean;
export declare const isLogsTab: ({ currentTab }: RightPanelContext) => boolean;
export declare const isJsonTab: ({ currentTab }: RightPanelContext) => boolean;
