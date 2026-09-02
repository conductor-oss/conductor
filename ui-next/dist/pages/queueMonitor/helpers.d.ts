import { FilterOption, FilterOptions, FetchResponse, PollData, QueueData, RangeOptions } from "./state/types";
export type QueueSummary = Partial<PollData> & {
    queueName: string;
    size: number;
    pollerCount: number;
};
/**
 * Adapts the two OSS queue endpoints to the combined response shape consumed
 * by the queue monitor. Domain-specific poll records use the same queue key as
 * the queue-size endpoint (`domain:taskType`).
 */
export declare const createQueueMonitorResponse: (queueSizes: Record<string, number>, pollData: PollData[], filterOptions: FilterOptions) => FetchResponse;
/**
 * Combines queue sizes with polling details without interpreting queue names as
 * object paths. Queue names are opaque identifiers and can validly contain
 * dots, brackets, and other path-like characters.
 */
export declare const createQueueSummaries: (pollDataByQueueName: Record<string, PollData[]>, queueData: QueueData) => QueueSummary[];
interface QueueMonitorRoute {
    workerSize?: string;
    workerOpt?: RangeOptions;
    queueSize?: string;
    queueOpt?: RangeOptions;
    lastPollTimeSize?: string;
    lastPollTimeOpt?: RangeOptions;
}
export declare const filterOptionOrNot: (prefix: string, matchParams: QueueMonitorRoute) => FilterOption | undefined;
export declare const renameKeys: (someObj: Record<string, unknown>, newNames: Record<string, string>) => Record<string, unknown>;
export declare const filterOptionToQueryParams: (filterOptions: FilterOptions) => string;
export declare const hasNoQueryParams: (filterOptions: FilterOptions) => boolean;
export declare const lastPollTimeColumnRenderer: (lastPollTime: number) => string;
export {};
