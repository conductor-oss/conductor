import React from "react";
import { IdempotencyValuesProp } from "../../definition/RunWorkflow/state";
import { ScheduleType } from "../Schedule";
export interface UseScheduleFormHandlersReturn {
    setScheduleNewState: (key: string, value: string) => void;
    setZoneId: (value: string) => void;
    setCronPausedState: () => void;
    setWorkflowInputTemplatesState: (value: string) => void;
    setWorkflowTasksToDomainState: (value: string) => void;
    setWorkflowCorrelationIdState: (value: string) => void;
    handleIdempotencyValues: (data: IdempotencyValuesProp) => void;
    handleScheduleStartTime: (value: number) => void;
    handleScheduleEndTime: (value: number) => void;
    getHighlightedPart: (value: string, selectionStart: number) => void;
}
export declare function useScheduleFormHandlers(scheduleState: ScheduleType, setScheduleState: React.Dispatch<React.SetStateAction<ScheduleType>>, setErrors: React.Dispatch<React.SetStateAction<any>>, clearError: (field: string) => void, errors: any, setCouldNotParseJson: (value: boolean) => void, setHighlightedPart: (part: number | null) => void): UseScheduleFormHandlersReturn;
