import _get from "lodash/get";
import { timestampRendererLocal } from "utils/index";
import { tryToJson } from "utils/index";
import { WorkflowDef } from "types/WorkflowDef";
import { CronScheduleEntry, ScheduleType } from "../Schedule";

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
 * Build the cronSchedules array from form state.
 * The primary cron (cronExpression + zoneId) becomes the first entry;
 * any additional entries follow.
 */
export function buildCronSchedules(
  scheduleState: Pick<
    ScheduleType,
    "cronExpression" | "zoneId" | "cronSchedules"
  >,
): CronScheduleEntry[] {
  const primary: CronScheduleEntry = {
    cronExpression: scheduleState.cronExpression || "",
    zoneId: scheduleState.zoneId || "UTC",
  };
  return [primary, ...(scheduleState.cronSchedules || [])];
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

  const body: Record<string, any> = {
    id: _get(schedule, "id"),
    paused: scheduleState.paused,
    runCatchupScheduleInstances: scheduleState.runCatchupScheduleInstances,
    name: scheduleState.name,
    description: scheduleState.description,
    cronSchedules: buildCronSchedules(scheduleState),
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

  // Extract primary cron from cronSchedules[0], fall back to legacy cronExpression
  const cronSchedules: CronScheduleEntry[] | undefined =
    changedData?.cronSchedules && changedData.cronSchedules.length > 0
      ? changedData.cronSchedules
      : undefined;

  const primaryCron = cronSchedules?.[0];
  const cronExpression =
    primaryCron?.cronExpression ?? changedData?.cronExpression ?? "";
  const zoneId = primaryCron?.zoneId ?? changedData?.zoneId ?? "UTC";
  const additionalSchedules =
    cronSchedules && cronSchedules.length > 1
      ? cronSchedules.slice(1)
      : undefined;

  const body = {
    name: changedData?.name || "",
    description: changedData?.description || "",
    cronExpression,
    cronSchedules: additionalSchedules,
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
    zoneId,
  };

  return body;
}
