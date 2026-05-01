import React, { useMemo, useState } from "react";
import { timestampRendererLocal } from "utils/date";
import { getTemplateFromInputParams } from "../../runWorkflow/runWorkflowUtils";
import { ScheduleType } from "../Schedule";

export interface UseScheduleStateReturn {
  scheduleState: ScheduleType;
  setScheduleState: React.Dispatch<React.SetStateAction<ScheduleType>>;
  original: Partial<ScheduleType>;
  setOriginal: React.Dispatch<React.SetStateAction<Partial<ScheduleType>>>;
  initializeFromSchedule: (schedule: any) => void;
  initializeFromExecution: (latestExecution: any) => void;
}

const initialState: ScheduleType = {
  name: "",
  description: "",
  cronExpression: "",
  cronSchedules: undefined,
  paused: false,
  runCatchupScheduleInstances: false,
  workflowType: null,
  workflowVersion: null,
  workflowVersions: [],
  workflowInputTemplate: "",
  taskToDomain: "",
  workflowCorrelationId: "",
  workflowIdempotencyKey: undefined,
  workflowIdempotencyStrategy: undefined,
  workflowDef: null,
  externalInputPayloadStoragePath: undefined,
  scheduleStartTime: "",
  scheduleEndTime: "",
  priority: "",
  zoneId: "UTC",
};

export function useScheduleState(
  latestExecution: any,
  _schedule: any,
): UseScheduleStateReturn {
  const memorizedState = useMemo(
    () => ({
      ...initialState,
      workflowType: latestExecution?.workflowName || null,
      workflowVersion: latestExecution?.workflowVersion
        ? `${latestExecution?.workflowVersion}`
        : null,
      workflowInputTemplate:
        latestExecution?.workflowDefinition?.inputParameters &&
        latestExecution.workflowDefinition.inputParameters.length > 0
          ? getTemplateFromInputParams(
              latestExecution?.workflowDefinition?.inputParameters,
            )
          : "",
      taskToDomain: latestExecution?.taskToDomain
        ? JSON.stringify(latestExecution.taskToDomain, null, 2)
        : "",
    }),
    [latestExecution],
  );

  const [scheduleState, setScheduleState] =
    useState<ScheduleType>(memorizedState);
  const [original, setOriginal] = useState<Partial<ScheduleType>>({
    paused: false,
    runCatchupScheduleInstances: false,
    name: "",
    description: "",
    cronExpression: "",
    scheduleStartTime: "",
    scheduleEndTime: "",
    zoneId: "UTC",
    startWorkflowRequest: {
      name: null,
      version: null,
      input: {},
      correlationId: "",
      taskToDomain: {},
      priority: "",
    },
  });

  const initializeFromSchedule = useMemo(
    () => (schedule: any) => {
      if (!schedule) return;

      const swr = schedule.startWorkflowRequest || {};
      const workflowInput = swr.input ? JSON.stringify(swr.input, null, 2) : "";
      const taskToDomainStr = swr.taskToDomain
        ? JSON.stringify(swr.taskToDomain, null, 2)
        : "";

      // Extract primary cron from cronSchedules[0], fall back to legacy cronExpression
      const serverCronSchedules =
        schedule.cronSchedules && schedule.cronSchedules.length > 0
          ? schedule.cronSchedules
          : null;
      const primaryCron = serverCronSchedules?.[0];
      const cronExpression =
        primaryCron?.cronExpression ?? schedule.cronExpression ?? "";
      const zoneId = primaryCron?.zoneId ?? schedule.zoneId ?? "UTC";
      const additionalSchedules =
        serverCronSchedules && serverCronSchedules.length > 1
          ? serverCronSchedules.slice(1)
          : undefined;

      const newState = {
        name: schedule.name,
        description: schedule.description || "",
        cronExpression,
        cronSchedules: additionalSchedules,
        runCatchupScheduleInstances: schedule.runCatchupScheduleInstances,
        paused: schedule.paused,
        workflowType: swr.name,
        workflowVersions: [], // Will be set by workflow config hook
        workflowVersion: swr.version ? `${swr.version}` : "",
        workflowCorrelationId: swr.correlationId,
        workflowIdempotencyKey: swr?.idempotencyKey,
        workflowIdempotencyStrategy: swr?.idempotencyStrategy,
        workflowInputTemplate: workflowInput,
        taskToDomain: taskToDomainStr,
        workflowDef: JSON.stringify(swr.workflowDef),
        externalInputPayloadStoragePath: swr.externalInputPayloadStoragePath,
        priority: swr.priority,
        scheduleStartTime: schedule.scheduleStartTime
          ? timestampRendererLocal(schedule.scheduleStartTime)
          : "",
        scheduleEndTime: schedule.scheduleEndTime
          ? timestampRendererLocal(schedule.scheduleEndTime)
          : "",
        zoneId,
      };

      setScheduleState((prevState) => ({ ...prevState, ...newState }));
      setOriginal({
        paused: schedule.paused,
        runCatchupScheduleInstances: schedule.runCatchupScheduleInstances,
        name: schedule.name,
        description: schedule.description,
        cronExpression,
        cronSchedules: additionalSchedules,
        scheduleStartTime: schedule.scheduleStartTime
          ? schedule.scheduleStartTime
          : "",
        scheduleEndTime: schedule.scheduleEndTime
          ? schedule.scheduleEndTime
          : "",
        startWorkflowRequest: {
          name: swr.name,
          version: swr.version ? `${swr.version}` : "",
          input: JSON.parse(workflowInput || "{}"),
          correlationId: swr.correlationId,
          idempotencyKey: swr?.idempotencyKey,
          idempotencyStrategy: swr?.idempotencyStrategy,
          taskToDomain: JSON.parse(taskToDomainStr || "{}"),
          externalInputPayloadStoragePath: swr.externalInputPayloadStoragePath,
          priority: swr.priority,
        },
        zoneId,
      });
    },
    [],
  );

  const initializeFromExecution = useMemo(
    () => (latestExecution: any) => {
      if (!latestExecution?.workflowName) return;

      const newState = {
        workflowVersions: [], // Will be populated by workflow config hook
      };

      setScheduleState((prevState) => ({ ...prevState, ...newState }));
    },
    [],
  );

  return {
    scheduleState,
    setScheduleState,
    original,
    setOriginal,
    initializeFromSchedule,
    initializeFromExecution,
  };
}
