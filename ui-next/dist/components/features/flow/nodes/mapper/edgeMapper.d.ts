import { CommonTaskDef } from "types";
export declare const edgeMapper: (currentTask: CommonTaskDef, previousTask?: CommonTaskDef, previousTaskAllowsConnection?: boolean) => ({
    data: {
        status: import("types").TaskStatus;
        unreachableEdge: boolean;
        delayedEdge: boolean | undefined;
    };
    id: string;
    from: string;
    fromPort: string;
    toPort: string;
    to: string;
} | {
    data: {
        unreachableEdge: boolean;
        delayedEdge: boolean | undefined;
    };
    id: string;
    from: string;
    fromPort: string;
    toPort: string;
    to: string;
})[];
