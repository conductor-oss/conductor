import * as cronjsMatcher from "@datasert/cronjs-matcher";
import cronstrue from "cronstrue";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { cronExpressionIsValid } from "utils/cronHelpers";
import { formatInTimeZone } from "utils/date";

export interface UseCronExpressionReturn {
  cronExpression: string;
  setCronExpression: (value: string, timezone: string) => void;
  futureMatches: string[];
  humanizedExpression: string;
  cronError: string | undefined;
  highlightedPart: number | null;
  setHighlightedPart: (part: number | null) => void;
}

const resolveTimezone = (value?: string) => value || "UTC";

/**
 * Calculate future matches for cron expressions with L-n pattern (e.g., L-2 for 2 days before last day of month)
 * since JavaScript cron libraries don't support this Quartz syntax
 */
function calculateLastDayOffsetMatches(
  cronExpression: string,
  timezone: string,
  count: number = 10,
): string[] {
  // Parse cron expression: seconds minutes hours dayOfMonth month dayOfWeek
  const parts = cronExpression.trim().split(/\s+/);
  if (parts.length < 6) {
    return [];
  }

  const [seconds, minutes, hours, dayOfMonth, month, dayOfWeek] = parts;

  // Extract offset from L-n pattern
  const match = dayOfMonth.match(/^L-(\d+)$/);
  if (!match) {
    return [];
  }

  const offset = parseInt(match[1], 10);
  const matches: string[] = [];
  const now = new Date();
  // eslint-disable-next-line prefer-const
  let currentDate = new Date(now);

  // Look ahead up to 24 months to find matches
  let monthsChecked = 0;
  while (matches.length < count && monthsChecked < 24) {
    const year = currentDate.getFullYear();
    const monthNum = currentDate.getMonth();

    // Get last day of month
    const lastDayOfMonth = new Date(year, monthNum + 1, 0);
    const targetDay = lastDayOfMonth.getDate() - offset;

    if (targetDay > 0) {
      // Set time from cron expression
      const hour = hours === "*" || hours === "?" ? 0 : parseInt(hours, 10);
      const minute =
        minutes === "*" || minutes === "?" ? 0 : parseInt(minutes, 10);
      const second =
        seconds === "*" || seconds === "?" ? 0 : parseInt(seconds, 10);

      // Create potential match date in UTC
      const matchDate = new Date(
        Date.UTC(year, monthNum, targetDay, hour, minute, second),
      );

      // Only include if it's in the future
      if (matchDate > now) {
        // Check day of week constraint if specified
        if (dayOfWeek !== "*" && dayOfWeek !== "?") {
          const dow = matchDate.getDay(); // 0 = Sunday
          const expectedDow = parseInt(dayOfWeek, 10);
          if (dow !== expectedDow) {
            currentDate.setMonth(currentDate.getMonth() + 1);
            monthsChecked++;
            continue;
          }
        }

        // Check month constraint if specified
        if (month !== "*" && month !== "?") {
          const expectedMonth = parseInt(month, 10);
          if (monthNum + 1 !== expectedMonth) {
            currentDate.setMonth(currentDate.getMonth() + 1);
            monthsChecked++;
            continue;
          }
        }

        // Format in the specified timezone
        const formatted = formatInTimeZone(
          matchDate,
          "yyyy-MM-dd HH:mm:ss",
          timezone,
        );
        matches.push(formatted);
      }
    }

    // Move to next month
    currentDate.setMonth(currentDate.getMonth() + 1);
    monthsChecked++;
  }

  return matches;
}

export function useCronExpression(
  initialCronExpression: string = "",
  timezone: string = "UTC",
  onError?: (error: string | undefined) => void,
): UseCronExpressionReturn {
  const [cronExpression, setCronExpressionState] = useState(
    initialCronExpression,
  );
  const [activeTimezone, setActiveTimezone] = useState(() =>
    resolveTimezone(timezone),
  );
  const [highlightedPart, setHighlightedPart] = useState<number | null>(null);

  // Sync with props changes
  const prevInitialRef = useRef(initialCronExpression);
  const prevTimezoneRef = useRef(timezone);

  if (
    initialCronExpression !== prevInitialRef.current ||
    timezone !== prevTimezoneRef.current
  ) {
    setCronExpressionState(initialCronExpression);
    setActiveTimezone(resolveTimezone(timezone));
    prevInitialRef.current = initialCronExpression;
    prevTimezoneRef.current = timezone;
  }

  const validation = useMemo(() => {
    if (!cronExpression.trim()) {
      return {
        matches: [] as string[],
        humanized: "",
        error: undefined,
      };
    }

    try {
      const validation = cronExpressionIsValid(cronExpression);
      if (!validation.isValid) {
        return {
          matches: [],
          humanized: "",
          error: validation.errors || "Invalid cron expression",
        };
      }

      // Check if expression contains Quartz L-n offset pattern (e.g., L-2)
      // JavaScript cron libraries don't support this, so we calculate manually
      const hasLastDayOffset = /\bL-\d+\b/.test(cronExpression);

      let matches: string[] = [];
      let matchError: string | undefined;

      if (hasLastDayOffset) {
        try {
          matches = calculateLastDayOffsetMatches(
            cronExpression,
            activeTimezone || "UTC",
            10,
          );
          if (matches.length === 0) {
            matchError =
              "Unable to calculate future matches for this expression";
          }
        } catch {
          matchError =
            "Failed to calculate schedule times. Please verify the expression.";
        }
      } else {
        try {
          matches = cronjsMatcher.getFutureMatches(cronExpression, {
            hasSeconds: true,
            timezone: activeTimezone || "UTC",
          });
        } catch {
          // If getFutureMatches fails, the expression likely won't work
          matchError =
            "Unable to calculate future matches. This expression may not be supported.";
        }
      }

      return {
        matches: matches.length > 0 ? matches : [],
        humanized: cronstrue.toString(cronExpression),
        error: matchError,
      };
    } catch {
      return {
        matches: [],
        humanized: "",
        error: "Invalid cron expression format",
      };
    }
  }, [cronExpression, activeTimezone]);

  // Use ref to avoid re-triggering effect when onError changes
  const onErrorRef = useRef(onError);
  useEffect(() => {
    onErrorRef.current = onError;
  });

  useEffect(() => {
    onErrorRef.current?.(validation.error);
  }, [validation.error]);

  const setCronExpression = useCallback((value: string, tz: string) => {
    setCronExpressionState(value);
    setActiveTimezone(resolveTimezone(tz));
  }, []);

  return {
    cronExpression,
    setCronExpression,
    futureMatches: validation.matches,
    humanizedExpression: validation.humanized,
    cronError: validation.error,
    highlightedPart,
    setHighlightedPart,
  };
}
