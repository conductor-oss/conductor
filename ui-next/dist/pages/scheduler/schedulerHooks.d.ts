import { UseMutationOptions, UseMutationResult } from "react-query";
import { IScheduleDto } from "types/Schedulers";
export declare function useSchedule(name: string | null | undefined): import("react-query").UseQueryResult<IScheduleDto, any>;
/**
 * Returns an async function that checks whether a schedule with the given name
 * already exists. Resolves to `true` if it does, `false` on 404.
 * Used by the clone dialog to detect duplicates at submit time without loading
 * a full schedule list on page mount.
 */
export declare function useCheckScheduleExists(): (name: string) => Promise<boolean>;
export interface SaveScheduleVariables {
    body: string;
    overwrite?: boolean;
}
export type UseSaveScheduleOptions = Omit<UseMutationOptions<void, Response, SaveScheduleVariables>, "mutationFn">;
export declare function useSaveSchedule({ onSuccess, ...callbacks }?: UseSaveScheduleOptions): UseMutationResult<void, Response, SaveScheduleVariables>;
