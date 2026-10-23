import React, { useMemo, useState } from "react";
import { timestampRendererLocal } from "utils/date";
import { getTemplateFromInputParams } from "../../runWorkflow/runWorkflowUtils";
import { CronSchedule } from "types/Schedulers";
import { ScheduleType } from "../Schedule";

/**
 * The schedule as it came back from the server, kept to compare the form against. It is wire
 * shaped rather than form shaped, so it carries cronSchedules rather than extraCronSchedules.
 */
type OriginalSchedule = Partial<ScheduleType> & {
  cronSchedules?: CronSchedule[];
};

export interface UseScheduleStateReturn {
  scheduleState: ScheduleType;
  setScheduleState: React.Dispatch<React.SetStateAction<ScheduleType>>;
  original: OriginalSchedule;
  setOriginal: React.Dispatch<React.SetStateAction<OriginalSchedule>>;
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
  zoneId: "UTC",
  extraCronSchedules: [],
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
  const [original, setOriginal] = useState<OriginalSchedule>({
    paused: false,
    runCatchupScheduleInstances: false,
    name: "",
    description: "",
    cronExpression: "",
    scheduleStartTime: "",
    scheduleEndTime: "",
    zoneId: "UTC",
    extraCronSchedules: [],
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
      const saved: CronSchedule[] = schedule.cronSchedules ?? [];
      let cronExpression = saved.length
        ? saved[0].cronExpression
        : schedule.cronExpression;
      if (cronExpression === null || cronExpression === undefined) {
        cronExpression = "";
      }
      const zoneId = saved.length ? saved[0].zoneId : schedule.zoneId;

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
        zoneId: zoneId,
        extraCronSchedules: saved.slice(1),
      };

      setScheduleState((prevState) => ({ ...prevState, ...newState }));
      setOriginal({
        paused: schedule.paused,
        runCatchupScheduleInstances: schedule.runCatchupScheduleInstances,
        name: schedule.name,
        description: schedule.description,
        cronExpression: cronExpression,
        // The baseline has to carry the list too, or codeToFormData splits a different head and
        // tail from it than the form holds, and an untouched multi-cron schedule looks edited.
        cronSchedules: saved,
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
        zoneId: schedule.zoneId,
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
