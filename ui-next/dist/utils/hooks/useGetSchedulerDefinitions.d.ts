import { IScheduleDto, SchedulerSearchResult } from "types/Schedulers";
/** Full schedule list (used by Scheduler Executions name dropdown / similar lookups). */
export declare const useGetSchedulerDefinitions: () => import("react-query").UseQueryResult<IScheduleDto[], unknown>;
export interface SchedulerSearchParams {
    start?: number;
    size?: number;
    sort?: string;
    workflowName?: string;
    name?: string;
    paused?: boolean;
}
export declare const useGetSchedulerDefinitionsWithPagination: (searchParams: SchedulerSearchParams) => import("react-query").UseQueryResult<SchedulerSearchResult, unknown>;
export declare function useScheduleNames(): string[];
