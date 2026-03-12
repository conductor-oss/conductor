import { AdapterDateFns } from "@mui/x-date-pickers/AdapterDateFns";
import {
  addMinutes,
  differenceInSeconds,
  Duration,
  endOfDay,
  format,
  formatDistance,
  formatDistanceToNow,
  fromUnixTime,
  intervalToDuration,
  isValid,
  parseISO,
  setMilliseconds,
  setMinutes,
  setSeconds,
  startOfDay,
  subDays,
  subHours,
  subMinutes,
} from "date-fns";
import {
  format as formatTz,
  formatInTimeZone as formatInTz,
  toDate,
  utcToZonedTime,
  zonedTimeToUtc,
} from "date-fns-tz";
import { isNil as _isNil } from "lodash";
import _isEmpty from "lodash/isEmpty";

export function durationRenderer(durationMs: number) {
  const duration: Duration = intervalToDuration({ start: 0, end: durationMs });
  if (durationMs > 5000) {
    if (duration?.months != null && duration.months > 0) {
      return `${duration.months} months ${duration.days}d ${duration.hours}h ${duration.minutes}m ${duration.seconds}s`;
    }
    if (duration?.days != null && duration?.days > 0) {
      return `${duration.days}d ${duration.hours}h ${duration.minutes}m ${duration.seconds}s`;
    } else if (duration?.hours != null && duration.hours > 0) {
      return `${duration.hours}h ${duration.minutes}m ${duration.seconds}s`;
    } else {
      return `${duration.minutes}m ${duration.seconds}s`;
    }
  } else {
    return `${durationMs}ms`;
  }

  //return !isNaN(durationMs) && (durationMs > 0? formatDuration({seconds: durationMs/1000}): '0.0 seconds');
}

export function timestampRenderer(date?: number | string) {
  return !_isNil(date) && Number(date) !== 0
    ? format(new Date(date), "yyyy-MM-dd HH:mm:ss")
    : ""; // could be string or number.
}

export function timestampRendererLocal(date?: number | string) {
  if (!_isNil(date) && Number(date) !== 0) {
    try {
      const newDate = new Date(date);
      return format(newDate, "yyyy-MM-dd'T'HH:mm");
    } catch {
      return "";
    }
  } else {
    return "";
  }
}

// Functions exported directly from date-fns
export { addMinutes, differenceInDays, parse } from "date-fns";

const DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
export const DATE_FORMAT = "yyyy-MM-dd HH:mm";

export const EXPECTED_DATE_FORMAT = "yyyy-MM-dd hh:mm a";

export const DateAdapter = AdapterDateFns;

export const getDateTime = (
  timeframe: string,
  count: string,
  unit: string,
  roundToMinute = false,
) => {
  const now = new Date();
  const result = new Date(now);

  switch (unit) {
    case "seconds":
      if (timeframe === "last") {
        result.setSeconds(result.getSeconds() - Number(count));
      } else {
        result.setSeconds(result.getSeconds() + Number(count));
      }
      break;
    case "minutes":
      if (timeframe === "last") {
        result.setMinutes(result.getMinutes() - Number(count));
      } else {
        result.setMinutes(result.getMinutes() + Number(count));
      }
      break;
    case "hours":
      if (timeframe === "last") {
        result.setHours(result.getHours() - Number(count));
      } else {
        result.setHours(result.getHours() + Number(count));
      }
      break;
    case "days":
      if (timeframe === "last") {
        result.setDate(result.getDate() - Number(count));
      } else {
        result.setDate(result.getDate() + Number(count));
      }
      break;
    case "weeks":
      if (timeframe === "last") {
        result.setDate(result.getDate() - Number(count) * 7);
      } else {
        result.setDate(result.getDate() + Number(count) * 7);
      }
      break;

    default:
      if (timeframe === "last") {
        result.setMonth(result.getMonth() - Number(count));
      } else {
        result.setMonth(result.getMonth() + Number(count));
      }
      break;
  }

  if (roundToMinute) {
    result.setSeconds(0);
    result.setMilliseconds(0);
  }

  return result.toString();
};

export const commonlyUsedDateTime = (timeKey: string) => {
  const now = new Date();
  const time = new Date(now);
  switch (timeKey) {
    case "yesterday":
      time.setDate(time.getDate() - 1);
      return {
        rangeStart: time.setHours(0, 0, 0, 0).toString(),
        rangeEnd: time.setHours(23, 59, 59, 999).toString(),
        name: "Yesterday",
      };
    case "last15Minutes":
      return {
        rangeStart: time.setMinutes(time.getMinutes() - 15).toString(),
        rangeEnd: "",
        name: "Last 15 Minutes",
      };
    case "last30Minutes":
      return {
        rangeStart: time.setMinutes(time.getMinutes() - 30).toString(),
        rangeEnd: "",
        name: "Last 30 Minutes",
      };
    case "last48Hours":
      return {
        rangeStart: time.setHours(time.getHours() - 48).toString(),
        name: "Last 48 Hours",
        rangeEnd: "",
      };
    case "last1Hour":
      return {
        rangeStart: time.setHours(time.getHours() - 1).toString(),
        name: "Last 1 Hour",
        rangeEnd: "",
      };
    case "last72Hours":
      return {
        rangeStart: time.setHours(time.getHours() - 72).toString(),
        name: "Last 72 Hours",
        rangeEnd: "",
      };
    case "last4Hours":
      return {
        rangeStart: time.setHours(time.getHours() - 4).toString(),
        name: "Last 4 Hours",
        rangeEnd: "",
      };
    case "lastWeek":
      return {
        rangeStart: time.setDate(time.getDate() - 7).toString(),
        name: "Last Week",
        rangeEnd: "",
      };
    case "last12Hours":
      return {
        rangeStart: time.setHours(time.getHours() - 12).toString(),
        name: "Last 12 Hours",
        rangeEnd: "",
      };

    default:
      return {
        rangeStart: time.setHours(0, 0, 0, 0).toString(),
        name: "Today",
        rangeEnd: time.setHours(23, 59, 59, 999).toString(),
      };
  }
};

export const getSearchDateTime = (start: string, end: string) => {
  const formattedStartDate = new Date(Number(start)).toLocaleDateString(
    "en-US",
    { month: "short", day: "numeric", year: "numeric" },
  );
  const formattedEndDate = new Date(Number(end)).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
  const startTime = new Date(Number(start)).toLocaleTimeString("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  });
  const endTime = new Date(Number(end)).toLocaleTimeString("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  });

  if (!start) {
    return `${formattedEndDate} @ ${endTime}`;
  } else if (!end) {
    return `${formattedStartDate} @ ${startTime}`;
  } else {
    return `${formattedStartDate} @ ${startTime} - ${formattedEndDate} @ ${endTime}`;
  }
};

export const formatDateTo24Hrs = (dateString: string) => {
  return format(new Date(Number(dateString)), DATE_TIME_FORMAT);
};

export const getRefreshRate = (count: string, type: string) => {
  switch (type) {
    case "minutes":
      return `${Number(count) * 60 * 1000}`;
    case "hours":
      return `${Number(count) * 60 * 60 * 1000}`;
    default:
      return `${Number(count) * 1000}`;
  }
};

export const getCombineDateTime = (date: string, time: string) => {
  // Extracting date components from date
  const startDate = new Date(Number(date));
  const year = startDate.getFullYear();
  const month = startDate.getMonth();
  const day = startDate.getDate();

  // Extracting time components from time
  const startTime = new Date(Number(time));
  const hours = startTime.getHours();
  const minutes = startTime.getMinutes();
  const seconds = startTime.getSeconds();

  // Creating a new Date object with combined date and time
  const combinedDateTime = new Date(year, month, day, hours, minutes, seconds);

  return combinedDateTime.getTime().toString();
};

export const printableUpdatedTime = (updatedTimeInMillis?: number): string => {
  if (updatedTimeInMillis == null || updatedTimeInMillis === 0) {
    return "0 minutes ago";
  }
  const printableUpdatedTime = formatDistanceToNow(
    new Date(updatedTimeInMillis),
    {
      addSuffix: true,
    },
  );
  return printableUpdatedTime;
};

export const maybeFormatDate = (dateString: string): string => {
  if (_isEmpty(dateString)) {
    return dateString;
  }
  if (isNaN(Number(dateString))) {
    return dateString;
  }
  const formattedDate = format(
    new Date(Number(dateString)),
    EXPECTED_DATE_FORMAT,
  );
  return formattedDate;
};

export const dateToEpoch = (dateString: string): number => {
  // Convert the Date object to epoch time (milliseconds since 1970/01/01 UTC)
  const epoch = new Date(
    isNaN(Number(dateString)) ? dateString : Number(dateString),
  ).getTime();

  return epoch;
};

export const convertToDateObject = (
  date: Date | string | null | undefined,
): Date | null => {
  if (!date) return null;

  if (date instanceof Date) {
    return isValid(date) ? date : null;
  }

  const parsed = parseISO(date);
  return isValid(parsed) ? parsed : null;
};

export const formatDate = (
  date: Date | string | number | null | undefined,
  dateFormat: string,
): string => {
  if (!date) return "";

  let parsedDate: Date;

  if (typeof date === "number") {
    parsedDate = date > 1e12 ? new Date(date) : fromUnixTime(date);
  } else if (typeof date === "string") {
    parsedDate = parseISO(date);
  } else {
    parsedDate = date;
  }

  if (!isValid(parsedDate)) return "";

  return format(parsedDate, dateFormat);
};

export const formatToDateTimeString = (
  date: Date | string | number | null | undefined,
): string => {
  return formatDate(date, DATE_TIME_FORMAT);
};

export const getStartOfDayTime = (startDate: Date | null) => {
  if (!startDate || !isValid(startDate)) return null;
  return startOfDay(startDate).getTime();
};

export const getEndOfDayTime = (endDate: Date | null) => {
  if (!endDate || !isValid(endDate)) return null;
  return endOfDay(endDate).getTime();
};

export interface TimeRangeTimestamps {
  start: number;
  end: number;
}

// Time unit mappings with their corresponding date-fns functions
const TIME_UNIT_MAP = {
  // Minutes
  min: subMinutes,
  m: subMinutes,
  minute: subMinutes,
  minutes: subMinutes,

  // Hours
  hr: subHours,
  h: subHours,
  hour: subHours,
  hours: subHours,

  // Days
  day: subDays,
  days: subDays,
  d: subDays,
} as const;

type TimeUnit = keyof typeof TIME_UNIT_MAP;

export const getTimeRangeTimestamps = (range: string): TimeRangeTimestamps => {
  const now = new Date();
  const nowTimestamp = now.getTime();

  if (!range) {
    // Default to 24 hours
    return { start: subDays(now, 1).getTime(), end: nowTimestamp };
  }

  // Clean up input: remove extra spaces and lowercase it
  const cleanedRange = range.trim().toLowerCase();

  // Split by whitespace to get parts
  const parts = cleanedRange.split(/\s+/);

  // We expect exactly 2 parts: number and unit
  if (parts.length !== 2) {
    // Fallback to 24 hours
    return { start: subDays(now, 1).getTime(), end: nowTimestamp };
  }

  const [valueStr, unitStr] = parts;
  const value = Number(valueStr);

  // Check if value is a valid number
  if (isNaN(value) || value <= 0) {
    return { start: subDays(now, 1).getTime(), end: nowTimestamp };
  }

  // Check if unit is supported
  if (!(unitStr in TIME_UNIT_MAP)) {
    return { start: subDays(now, 1).getTime(), end: nowTimestamp };
  }

  const unit = unitStr as TimeUnit;
  const subtractFunction = TIME_UNIT_MAP[unit];
  const startDate = subtractFunction(now, value);

  return {
    start: startDate.getTime(),
    end: nowTimestamp,
  };
};

/**
 * Formats a Unix timestamp (in seconds) as 'HH:mm:ss'.
 * @param unixSeconds Unix timestamp in seconds
 * @returns Formatted time string (e.g., '13:45:30')
 */
export const formatUnixTimeToTimeString = (unixSeconds: number): string => {
  return format(fromUnixTime(unixSeconds), "HH:mm:ss");
};

/**
 * Returns a Unix timestamp (in seconds) representing the time `hoursBack` ago from `fromTimestamp`.
 * If `fromTimestamp` is not provided, it defaults to now.
 */
export const getUnixTimestampHoursAgo = (
  hoursBack: number,
  fromTimestamp?: number,
): number => {
  const fromDate = fromTimestamp ? new Date(fromTimestamp * 1000) : new Date();
  const date = subHours(fromDate, hoursBack);
  return Math.floor(date.getTime() / 1000);
};

/**
 * Returns the current Unix timestamp in seconds.
 */
export const getCurrentUnixTimestamp = (): number => {
  return Math.floor(Date.now() / 1000);
};

/**
 * Returns the number of seconds between two dates or timestamps.
 * Accepts numbers (ms), strings (ISO), or Date objects.
 */
export const getDifferenceInSeconds = (
  from: Date | string | number,
  to: Date | string | number,
): number => {
  const fromDate = new Date(from);
  const toDate = new Date(to);

  if (isNaN(fromDate.getTime()) || isNaN(toDate.getTime())) {
    throw new Error("Invalid date input to getDifferenceInSeconds");
  }

  return differenceInSeconds(toDate, fromDate);
};

/**
 * Returns a human-friendly, fuzzy description of the time difference between two dates or timestamps.
 *
 * @param from - The starting date/time. Can be a Date object, ISO string, or timestamp (ms).
 * @param to - The ending date/time. Can be a Date object, ISO string, or timestamp (ms). Defaults to now.
 * @returns A string describing the approximate duration between the two dates (e.g., "about a minute", "2 hours").
 *          Returns an empty string if either input is invalid.
 *
 * @example
 * ```ts
 * humanizeDuration(Date.now() - 45000, Date.now()); // "about a minute"
 * humanizeDuration('2023-01-01T00:00:00Z');         // relative to now, e.g., "over 2 years"
 * ```
 */
export const humanizeDuration = (
  from: Date | string | number,
  to: Date | string | number = Date.now(),
): string => {
  const fromDate = new Date(from);
  const toDate = new Date(to);

  if (!isValid(fromDate) || !isValid(toDate)) return "";

  return formatDistance(fromDate, toDate);
};

/**
 * Returns the current date/time rounded down to the start of the current hour (minutes, seconds, ms = 0).
 */
export const startOfCurrentHour = (): Date => {
  const now = new Date();
  return setMilliseconds(setSeconds(setMinutes(now, 0), 0), 0);
};

/**
 * Adds specified number of minutes to a given date.
 * @param date Date to add minutes to
 * @param minutes number of minutes to add
 * @returns new Date with minutes added
 */
export const addMinutesToDate = (date: Date, minutes: number): Date => {
  return addMinutes(date, minutes);
};

/**
 * Returns a timezone offset like "+05:30" or "-04:00"
 * Equivalent to Moment's `.format("Z")`
 */
export const getMomentStyleOffset = (
  timeZone: string,
  date: Date = new Date(),
): string => {
  const zonedDate = utcToZonedTime(date, timeZone);
  const offsetMinutes = -zonedDate.getTimezoneOffset();

  const sign = offsetMinutes >= 0 ? "+" : "-";
  const abs = Math.abs(offsetMinutes);
  const hours = Math.floor(abs / 60)
    .toString()
    .padStart(2, "0");
  const minutes = (abs % 60).toString().padStart(2, "0");

  return `${sign}${hours}:${minutes}`;
};

/**
 * Returns a short time zone abbreviation (like "PDT", "IST", etc.)
 * Equivalent to Moment's `.format("zz")`
 */
export const getTimeZoneAbbreviation = (
  timeZone: string,
  date: Date = new Date(),
): string => {
  try {
    const formatter = new Intl.DateTimeFormat("en-US", {
      timeZone,
      timeZoneName: "short",
    });
    const parts = formatter.formatToParts(date);
    const tzPart = parts.find((p) => p.type === "timeZoneName");
    return tzPart?.value ?? "";
  } catch {
    return "";
  }
};

/**
 * Returns a list of all time zones.
 */
export const getTimeZoneNames = (): string[] => {
  return Intl.supportedValuesOf("timeZone");
};

/**
 * Guesses the system time zone
 */
export const guessUserTimeZone = (): string => {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
};

/**
 * Formats a date using timezone-aware formatting
 * @param date Date to format
 * @param formatString Format string (e.g., "MMM d, yyyy hh:mm a")
 * @param timeZone Timezone to use (defaults to system timezone)
 * @returns Formatted date string
 */
export const formatInTimeZone = (
  date: Date | string | number,
  formatString: string,
  timeZone?: string,
): string => {
  // Use the native formatInTimeZone from date-fns-tz
  if (timeZone) {
    return formatInTz(date, timeZone, formatString);
  }
  // Fallback to regular format if no timezone specified
  const dateObj =
    typeof date === "string" || typeof date === "number"
      ? new Date(date)
      : date;
  return formatTz(dateObj, formatString);
};

/**
 * Converts a date to a Date object using timezone-aware parsing
 * @param date Date to convert
 * @param timeZone Timezone to use (defaults to system timezone)
 * @returns Date object
 */
export const convertToDateInTimeZone = (
  date: Date | string | number,
  timeZone?: string,
): Date => {
  return toDate(date, { timeZone });
};

/**
 * Parses a date string that is in a specific timezone and converts it to a Date object (UTC internally)
 * This is useful when you have a date string like "2024-10-17T15:30:00" that represents a time in a specific timezone
 * @param dateString Date string to parse
 * @param timeZone Timezone the date string is in
 * @returns Date object (stored as UTC internally)
 */
export const parseDateInTimeZone = (
  dateString: string,
  timeZone: string,
): Date => {
  // If the string already has timezone info (Z or offset), just parse it normally
  if (dateString.includes("Z") || /[+-]\d{2}:\d{2}$/.test(dateString)) {
    return new Date(dateString);
  }
  // Otherwise, treat the string as being in the specified timezone
  return zonedTimeToUtc(dateString, timeZone);
};
