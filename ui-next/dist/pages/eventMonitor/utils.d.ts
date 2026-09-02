import { EventItem, GroupedEventItem } from "./types";
export declare const groupDataByMessageId: (data: EventItem[]) => GroupedEventItem[];
export declare const actions: {
    name: string;
    label: string;
}[];
export declare const status: {
    name: string;
    label: string;
}[];
export declare const statusColors: {
    [key: string]: string;
};
export declare const TIME_RANGE_OPTIONS: {
    label: string;
    value: number;
}[];
export declare const statusConfig: {
    readonly FAILED: {
        readonly label: "Failed";
        readonly color: "#FBB4C6";
    };
    readonly SKIPPED: {
        readonly label: "Skipped";
        readonly color: "#DDDDDD";
    };
    readonly IN_PROGRESS: {
        readonly label: "In Progress";
        readonly color: "#8DE0F9";
    };
    readonly COMPLETED: {
        readonly label: "Completed";
        readonly color: "#9FDCAA";
    };
};
export declare const truncatePayload: (payload: object) => string;
