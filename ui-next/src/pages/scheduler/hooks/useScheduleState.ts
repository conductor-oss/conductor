import React, { useMemo, useState } from "react";
import { timestampRendererLocal } from "utils/date";
import { getTemplateFromInputParams } from "../../runWorkflow/runWorkflowUtils";
import { ScheduleType } from "../Schedule";
import {
  DEFAULT_CRON_ZONE,
  getNormalizedCronSchedules,
  hasMultiCronSchedules,
} from "../utils/cronSchedules";

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
  zoneId: DEFAULT_CRON_ZONE,
  cronSchedules: [],
  cronMode: "single",
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
    cronSchedules: [],
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
      let cronExpression = schedule.cronExpression;
      if (cronExpression === null) {
        cronExpression = "";
      }
      const cronSchedules = getNormalizedCronSchedules(schedule.cronSchedules);
      const isMultiCron = hasMultiCronSchedules(schedule.cronSchedules);

      const newState = {
        name: schedule.name,
        description: schedule.description || "",
        cronExpression: cronExpression,
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
        zoneId: schedule.zoneId || DEFAULT_CRON_ZONE,
        cronSchedules,
        cronMode: isMultiCron ? ("multi" as const) : ("single" as const),
      };

      setScheduleState((prevState) => ({ ...prevState, ...newState }));
      setOriginal({
        paused: schedule.paused,
        runCatchupScheduleInstances: schedule.runCatchupScheduleInstances,
        name: schedule.name,
        description: schedule.description,
        scheduleStartTime: schedule.scheduleStartTime
          ? schedule.scheduleStartTime
          : "",
        scheduleEndTime: schedule.scheduleEndTime
          ? schedule.scheduleEndTime
          : "",
        ...(isMultiCron
          ? { cronSchedules }
          : {
              cronExpression: cronExpression,
              zoneId: schedule.zoneId || DEFAULT_CRON_ZONE,
            }),
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
