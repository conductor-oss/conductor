import * as cronjsMatcher from "@datasert/cronjs-matcher";
import { ICronSchedule, IScheduleDto } from "types/Schedulers";
import { cronExpressionIsValid } from "utils/cronHelpers";
import { parseDateInTimeZone } from "utils/date";

export const DEFAULT_CRON_ZONE = "UTC";

export const normalizeCronSchedule = (
  cronSchedule: Partial<ICronSchedule> | null | undefined,
): ICronSchedule => ({
  cronExpression: cronSchedule?.cronExpression || "",
  zoneId: cronSchedule?.zoneId || DEFAULT_CRON_ZONE,
});

export const getNormalizedCronSchedules = (
  cronSchedules: Array<Partial<ICronSchedule>> | null | undefined,
): ICronSchedule[] => {
  if (!cronSchedules?.length) {
    return [];
  }
  return cronSchedules.map((entry) => normalizeCronSchedule(entry));
};

export const hasMultiCronSchedules = (
  cronSchedules: Array<Partial<ICronSchedule>> | null | undefined,
): boolean => {
  return !!(cronSchedules && cronSchedules.length > 0);
};

export const getScheduleCronSchedules = (
  schedule: Partial<IScheduleDto> | null | undefined,
): ICronSchedule[] => {
  if (hasMultiCronSchedules(schedule?.cronSchedules)) {
    return getNormalizedCronSchedules(schedule?.cronSchedules);
  }
  if (schedule?.cronExpression) {
    return [
      {
        cronExpression: schedule.cronExpression,
        zoneId: schedule.zoneId || DEFAULT_CRON_ZONE,
      },
    ];
  }
  return [];
};

const getFirstFutureRunMs = (
  cronExpression: string,
  zoneId: string = DEFAULT_CRON_ZONE,
): number | null => {
  const validation = cronExpressionIsValid(cronExpression);
  if (!validation.isValid) {
    return null;
  }
  try {
    const matches = cronjsMatcher.getFutureMatches(cronExpression, {
      hasSeconds: true,
      timezone: zoneId,
      matchCount: 1,
    });
    if (!matches.length) {
      return null;
    }
    return parseDateInTimeZone(matches[0], zoneId).getTime();
  } catch {
    return null;
  }
};

export const getEarliestCronScheduleNextRun = (
  cronSchedules: Array<Partial<ICronSchedule>> | null | undefined,
): number | undefined => {
  const normalized = getNormalizedCronSchedules(cronSchedules);
  const allTimes = normalized
    .map((entry) =>
      getFirstFutureRunMs(
        entry.cronExpression,
        entry.zoneId || DEFAULT_CRON_ZONE,
      ),
    )
    .filter((value): value is number => value !== null);

  if (!allTimes.length) {
    return undefined;
  }
  return Math.min(...allTimes);
};
