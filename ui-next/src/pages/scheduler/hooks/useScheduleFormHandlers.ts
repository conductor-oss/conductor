import React, { useCallback } from "react";
import { IdempotencyValuesProp } from "../../definition/RunWorkflow/state";
import { IdempotencyStrategyEnum } from "../../runWorkflow/types";
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

const scheduleNamePattern = /^[a-zA-Z0-9_]+$/;

export function useScheduleFormHandlers(
  scheduleState: ScheduleType,
  setScheduleState: React.Dispatch<React.SetStateAction<ScheduleType>>,
  setErrors: React.Dispatch<React.SetStateAction<any>>,
  clearError: (field: string) => void,
  errors: any,
  setCouldNotParseJson: (value: boolean) => void,
  setHighlightedPart: (part: number | null) => void,
): UseScheduleFormHandlersReturn {
  const setScheduleNewState = useCallback(
    (key: string, value: string) => {
      // Validate name field
      if (key === "name") {
        if (!value.trim()) {
          // Set error for empty name
          setErrors((prevErrors: any) => ({
            ...prevErrors,
            name: "Name is required",
          }));
        } else if (!scheduleNamePattern.test(value)) {
          // Set error for invalid name pattern
          setErrors((prevErrors: any) => ({
            ...prevErrors,
            name: "Name can only contain letters, numbers, and underscores.",
          }));
        } else {
          // Clear error if name is valid
          if (errors?.name) {
            clearError("name");
          }
        }
      } else {
        // For other fields, just clear error if it exists
        if (errors?.[key]) {
          clearError(key);
        }
      }

      setScheduleState((prevState) => ({
        ...prevState,
        [key]: value,
      }));
    },
    [setScheduleState, setErrors, clearError, errors],
  );

  const setZoneId = useCallback(
    (value: string) => {
      if (errors?.zoneId) {
        clearError("zoneId");
      }
      setScheduleState((prevState) => ({
        ...prevState,
        zoneId: value,
      }));
    },
    [setScheduleState, clearError, errors],
  );

  const setCronPausedState = useCallback(() => {
    setScheduleState((prevState) => ({
      ...prevState,
      paused: !prevState.paused,
    }));
  }, [setScheduleState]);

  const setWorkflowInputTemplatesState = useCallback(
    (value: string) => {
      try {
        JSON.parse(value);
        setCouldNotParseJson(false);
      } catch {
        setCouldNotParseJson(true);
        return;
      }
      setScheduleState((prevState) => ({
        ...prevState,
        workflowInputTemplate: value,
      }));
    },
    [setScheduleState, setCouldNotParseJson],
  );

  const setWorkflowTasksToDomainState = useCallback(
    (value: string) => {
      try {
        JSON.parse(value);
        setCouldNotParseJson(false);
      } catch {
        setCouldNotParseJson(true);
        return;
      }
      setScheduleState((prevState) => ({
        ...prevState,
        taskToDomain: value,
      }));
    },
    [setScheduleState, setCouldNotParseJson],
  );

  const setWorkflowCorrelationIdState = useCallback(
    (value: string) => {
      setScheduleState((prevState) => ({
        ...prevState,
        workflowCorrelationId: value,
      }));
    },
    [setScheduleState],
  );

  const handleIdempotencyValues = useCallback(
    (data: IdempotencyValuesProp) => {
      const idempotencyStrategy = () => {
        if (data.idempotencyStrategy) {
          return data.idempotencyStrategy;
        }
        if (scheduleState?.workflowIdempotencyStrategy) {
          return scheduleState?.workflowIdempotencyStrategy;
        }
        return IdempotencyStrategyEnum.RETURN_EXISTING;
      };
      setScheduleState((prevState) => ({
        ...prevState,
        workflowIdempotencyKey: data?.idempotencyKey,
        workflowIdempotencyStrategy: data?.idempotencyKey
          ? idempotencyStrategy()
          : undefined,
      }));
    },
    [scheduleState, setScheduleState],
  );

  const handleScheduleStartTime = useCallback(
    (value: number) => {
      setScheduleState((prevState) => ({
        ...prevState,
        scheduleStartTime: value,
      }));
    },
    [setScheduleState],
  );

  const handleScheduleEndTime = useCallback(
    (value: number) => {
      setScheduleState((prevState) => ({
        ...prevState,
        scheduleEndTime: value,
      }));
    },
    [setScheduleState],
  );

  const getHighlightedPart = useCallback(
    (value: string, selectionStart: number) => {
      const partsUntilCursor = value.substring(0, selectionStart).split(" ");
      setHighlightedPart(partsUntilCursor.length - 1);
    },
    [setHighlightedPart],
  );

  return {
    setScheduleNewState,
    setZoneId,
    setCronPausedState,
    setWorkflowInputTemplatesState,
    setWorkflowTasksToDomainState,
    setWorkflowCorrelationIdState,
    handleIdempotencyValues,
    handleScheduleStartTime,
    handleScheduleEndTime,
    getHighlightedPart,
  };
}
