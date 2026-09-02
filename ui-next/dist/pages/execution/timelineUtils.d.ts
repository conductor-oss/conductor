import { ExecutionTask } from "types/Execution";
interface TimelineGroup {
    id: string;
    content: string;
    treeLevel?: number;
    nestedGroups?: string[];
}
interface TimelineItem {
    id: string;
    group: string;
    content: string;
    start: Date;
    end: Date;
    title: string;
    className: string;
    style?: string;
}
type ExecutionStatusMap = Record<string, {
    related?: unknown;
}>;
export declare const processTasksToGroupsAndItems: (tasks: ExecutionTask[], executionStatusMap: ExecutionStatusMap) => [TimelineGroup[], TimelineItem[]];
export {};
