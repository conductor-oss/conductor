import _get from "lodash/get";
import { timestampRendererLocal } from "utils/index";
import { tryToJson } from "utils/index";
import { CronSchedule } from "types/Schedulers";
import { WorkflowDef } from "types/WorkflowDef";
import { ScheduleType } from "../Schedule";

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
 * The cron half of a save payload. One expression goes as cronExpression/zoneId exactly as it
 * always has; several go as cronSchedules alone, the first entry being the one from the main
 * section. The server ignores cronExpression once the list is set, so sending both would leave
 * a second copy that nothing reads.
 *
 * Shared because the payload is assembled in three places — the save, the Code tab and the save
 * confirmation diff — and they have to agree.
 */
export function cronFieldsFor(
  scheduleState: ScheduleType,
):
  | { cronExpression: string; zoneId?: string }
  | { cronSchedules: CronSchedule[] } {
  const extras = scheduleState.extraCronSchedules ?? [];
  if (!extras.length) {
    return {
      cronExpression: scheduleState.cronExpression,
      zoneId: scheduleState.zoneId,
    };
  }
  return {
    cronSchedules: [
      {
        cronExpression: scheduleState.cronExpression,
        zoneId: scheduleState.zoneId || "UTC",
      },
      ...extras,
    ],
  };
}

/**
 * Convert form data to code representation
 */
export function formToCodeData(
  scheduleState: ScheduleType,
  schedule: any,
): (Partial<ScheduleType> & { cronSchedules?: CronSchedule[] }) | null {
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

  const cronFields = cronFieldsFor(scheduleState);

  const body = {
    id: _get(schedule, "id"),
    paused: scheduleState.paused,
    runCatchupScheduleInstances: scheduleState.runCatchupScheduleInstances,
    name: scheduleState.name,
    description: scheduleState.description,
    ...cronFields,
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
  // The first entry of cronSchedules fills the main section; the rest become extra rows.
  const saved: CronSchedule[] = changedData?.cronSchedules ?? [];
  const body = {
    name: changedData?.name || "",
    description: changedData?.description || "",
    cronExpression: saved.length
      ? saved[0].cronExpression
      : changedData?.cronExpression || "",
    extraCronSchedules: saved.slice(1),
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
    zoneId: saved.length ? saved[0].zoneId : changedData?.zoneId,
  };

  return body;
}
