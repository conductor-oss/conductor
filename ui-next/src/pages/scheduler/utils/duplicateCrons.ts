import { CronSchedule } from "types/Schedulers";

const identity = (cron: CronSchedule) =>
  `${cron.cronExpression.trim()}|${cron.zoneId || "UTC"}`;

/**
 * For each expression, the 1-based position of the earlier one it repeats, or undefined.
 *
 * The server queues one job per entry and tells them apart by position, not by content, so a
 * repeated expression is not collapsed — it fires the workflow twice at the same moment.
 * Blank entries are skipped; they are rows the user has not filled in yet.
 */
export const findDuplicateCrons = (
  crons: CronSchedule[],
): (number | undefined)[] => {
  const firstSeenAt = new Map<string, number>();

  return crons.map((cron, index) => {
    if (!cron.cronExpression.trim()) {
      return undefined;
    }
    const key = identity(cron);
    const earlier = firstSeenAt.get(key);
    if (earlier === undefined) {
      firstSeenAt.set(key, index);
      return undefined;
    }
    return earlier + 1;
  });
};

/**
 * The complaint to show when the schedule repeats an expression, or null when it does not.
 *
 * Checked before the save confirmation opens as well as on save itself: the confirmation
 * replaces the form with a diff, and a message raised from there lands behind the header.
 */
export const duplicateCronMessage = (crons: CronSchedule[]): string | null => {
  const duplicates = findDuplicateCrons(crons);
  const repeated = duplicates.findIndex((each) => each !== undefined);
  if (repeated === -1) {
    return null;
  }
  return (
    `Cron expression ${repeated + 1} is the same as cron expression ${duplicates[repeated]}. ` +
    `Remove one — the workflow would run twice.`
  );
};
