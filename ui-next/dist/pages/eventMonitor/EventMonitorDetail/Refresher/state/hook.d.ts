import { State } from "xstate";
import { PageType, RefreshMachineContext } from "./types";
export declare const useRefreshMachine: (pageType?: PageType, eventName?: string, timeRange?: number) => readonly [{
    readonly refreshInterval: number;
    readonly elapsed: number;
    readonly eventMonitorData: import("../../../types").GroupedEventItem[] | undefined;
    readonly isFetching: boolean;
    readonly eventListData: import("../../../types").EventExecutionResult | undefined;
    readonly isError: boolean;
}, {
    readonly changeRefreshRate: (value: number) => void;
    readonly handleRefresh: () => State<RefreshMachineContext, import("./types").TimerEvents, any, {
        value: any;
        context: RefreshMachineContext;
    }, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("./types").TimerEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
}];
