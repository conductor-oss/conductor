import React from "react";
import { ScheduleType } from "../Schedule";
export interface UseScheduleStateReturn {
    scheduleState: ScheduleType;
    setScheduleState: React.Dispatch<React.SetStateAction<ScheduleType>>;
    original: Partial<ScheduleType>;
    setOriginal: React.Dispatch<React.SetStateAction<Partial<ScheduleType>>>;
    initializeFromSchedule: (schedule: any) => void;
    initializeFromExecution: (latestExecution: any) => void;
}
export declare function useScheduleState(latestExecution: any, _schedule: any): UseScheduleStateReturn;
