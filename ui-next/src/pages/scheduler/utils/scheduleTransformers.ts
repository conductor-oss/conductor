import _get from "lodash/get";
import { ICronSchedule } from "types/Schedulers";
import { timestampRendererLocal } from "utils/index";
import { tryToJson } from "utils/index";
import { WorkflowDef } from "types/WorkflowDef";
import { ScheduleType } from "../Schedule";
import { DEFAULT_CRON_ZONE } from "./cronSchedules";

/**
 * Parse JSON string safely, returning null for empty strings
 */
export function JSONParse(text: string) {
  if (text) {
    return JSON.parse(text);
  }
  return null;
}

/**
 * Convert date field to timestamp value
 */
export function getDateFromField(d1: string | number | Date) {
  if (d1) {
    return new Date(d1).valueOf();
  }
  return "";
}

/**
 * Convert form data to code representation
 */
export function formToCodeData(
  scheduleState: ScheduleType,
  schedule: any,
): Partial<ScheduleType> | null {
  const start = getDateFromField(scheduleState.scheduleStartTime);
  const to = getDateFromField(scheduleState.scheduleEndTime);

  let input;
  try {
    input = JSONParse(scheduleState.workflowInputTemplate);
  } catch {
    return null;
  }

  let taskToDomain;
  try {
    taskToDomain = JSONParse(scheduleState.taskToDomain);
  } catch {
    return null;
  }

  const cronPayload =
    scheduleState.cronMode === "multi"
      ? {
          cronSchedules: scheduleState.cronSchedules?.map(
            (entry: ICronSchedule) => ({
              cronExpression: entry.cronExpression,
              zoneId: entry.zoneId || DEFAULT_CRON_ZONE,
            }),
          ),
        }
      : {
          cronExpression: scheduleState.cronExpression,
          zoneId: scheduleState.zoneId || DEFAULT_CRON_ZONE,
        };

  const body = {
    id: _get(schedule, "id"),
    paused: scheduleState.paused,
    runCatchupScheduleInstances: scheduleState.runCatchupScheduleInstances,
    name: scheduleState.name,
    description: scheduleState.description,
    ...cronPayload,
    scheduleStartTime: start,
    scheduleEndTime: to,
    startWorkflowRequest: {
      name: scheduleState.workflowType,
      version: scheduleState.workflowVersion,
      input: input ? input : {},
      correlationId: scheduleState.workflowCorrelationId,
      idempotencyKey: scheduleState?.workflowIdempotencyKey,
      idempotencyStrategy: scheduleState?.workflowIdempotencyStrategy,
      taskToDomain: taskToDomain ? taskToDomain : {},
      workflowDef: tryToJson<WorkflowDef>(scheduleState.workflowDef),
      externalInputPayloadStoragePath:
        scheduleState.externalInputPayloadStoragePath,
      priority: scheduleState.priority,
    },
  };

  return body;
}

/**
 * Convert code data to form representation
 */
export function codeToFormData(
  data: string,
  scheduleState: ScheduleType,
): ScheduleType {
  const changedData = tryToJson<any>(data);
  const body = {
    name: changedData?.name || "",
    description: changedData?.description || "",
    cronExpression: changedData?.cronExpression || "",
    cronSchedules: (changedData?.cronSchedules || []).map(
      (entry: ICronSchedule) => ({
        cronExpression: entry?.cronExpression || "",
        zoneId: entry?.zoneId || DEFAULT_CRON_ZONE,
      }),
    ),
    cronMode:
      changedData?.cronSchedules && changedData?.cronSchedules.length > 0
        ? ("multi" as const)
        : ("single" as const),
    runCatchupScheduleInstances: !!changedData?.runCatchupScheduleInstances,
    paused: !!changedData?.paused,
    workflowType: changedData?.startWorkflowRequest?.name,
    workflowVersions: scheduleState.workflowVersions,
    workflowVersion: changedData?.startWorkflowRequest?.version,
    workflowCorrelationId: changedData?.startWorkflowRequest?.correlationId,
    workflowIdempotencyKey: changedData?.startWorkflowRequest?.idempotencyKey,
    workflowIdempotencyStrategy:
      changedData?.startWorkflowRequest?.idempotencyStrategy,
    workflowInputTemplate: JSON.stringify(
      changedData?.startWorkflowRequest?.input,
      null,
      2,
    ),
    taskToDomain: JSON.stringify(
      changedData?.startWorkflowRequest?.taskToDomain,
      null,
      2,
    ),
    workflowDef: JSON.stringify(
      changedData?.startWorkflowRequest?.workflowDef,
      null,
      2,
    ),
    externalInputPayloadStoragePath:
      changedData?.startWorkflowRequest?.externalInputPayloadStoragePath,
    priority: changedData?.startWorkflowRequest?.priority,
    scheduleStartTime: changedData?.scheduleStartTime
      ? timestampRendererLocal(changedData?.scheduleStartTime)
      : "",
    scheduleEndTime: changedData?.scheduleEndTime
      ? timestampRendererLocal(changedData?.scheduleEndTime)
      : "",
    zoneId: changedData?.zoneId || DEFAULT_CRON_ZONE,
  };

  return body;
}
