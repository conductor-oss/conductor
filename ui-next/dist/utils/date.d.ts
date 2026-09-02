import { AdapterDateFns } from "@mui/x-date-pickers/AdapterDateFns";
export declare function durationRenderer(durationMs: number): string;
export declare function timestampRenderer(date?: number | string): string;
export declare function timestampRendererLocal(date?: number | string): string;
export { addMinutes, differenceInDays, parse } from "date-fns";
export declare const DATE_FORMAT = "yyyy-MM-dd HH:mm";
export declare const EXPECTED_DATE_FORMAT = "yyyy-MM-dd hh:mm a";
export declare const DateAdapter: typeof AdapterDateFns;
export declare const getDateTime: (timeframe: string, count: string, unit: string, roundToMinute?: boolean) => string;
export declare const commonlyUsedDateTime: (timeKey: string) => {
    rangeStart: string;
    rangeEnd: string;
    name: string;
};
export declare const getSearchDateTime: (start: string, end: string) => string;
export declare const formatDateTo24Hrs: (dateString: string) => string;
export declare const getRefreshRate: (count: string, type: string) => string;
export declare const getCombineDateTime: (date: string, time: string) => string;
export declare const printableUpdatedTime: (updatedTimeInMillis?: number) => string;
export declare const maybeFormatDate: (dateString: string) => string;
export declare const dateToEpoch: (dateString: string) => number;
export declare const convertToDateObject: (date: Date | string | null | undefined) => Date | null;
export declare const formatDate: (date: Date | string | number | null | undefined, dateFormat: string) => string;
export declare const formatToDateTimeString: (date: Date | string | number | null | undefined) => string;
export declare const getStartOfDayTime: (startDate: Date | null) => number | null;
export declare const getEndOfDayTime: (endDate: Date | null) => number | null;
export interface TimeRangeTimestamps {
    start: number;
    end: number;
}
export declare const getTimeRangeTimestamps: (range: string) => TimeRangeTimestamps;
/**
 * Formats a Unix timestamp (in seconds) as 'HH:mm:ss'.
 * @param unixSeconds Unix timestamp in seconds
 * @returns Formatted time string (e.g., '13:45:30')
 */
export declare const formatUnixTimeToTimeString: (unixSeconds: number) => string;
/**
 * Returns a Unix timestamp (in seconds) representing the time `hoursBack` ago from `fromTimestamp`.
 * If `fromTimestamp` is not provided, it defaults to now.
 */
export declare const getUnixTimestampHoursAgo: (hoursBack: number, fromTimestamp?: number) => number;
/**
 * Returns the current Unix timestamp in seconds.
 */
export declare const getCurrentUnixTimestamp: () => number;
/**
 * Returns the number of seconds between two dates or timestamps.
 * Accepts numbers (ms), strings (ISO), or Date objects.
 */
export declare const getDifferenceInSeconds: (from: Date | string | number, to: Date | string | number) => number;
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
export declare const humanizeDuration: (from: Date | string | number, to?: Date | string | number) => string;
/**
 * Formats the distance between two timestamps using the most useful unit and
 * includes direction. date-fns handles seconds and larger units; sub-second
 * values retain millisecond precision.
 */
export declare const formatRelativeDuration: (from: Date | string | number, to?: Date | string | number) => string;
/**
 * Returns the current date/time rounded down to the start of the current hour (minutes, seconds, ms = 0).
 */
export declare const startOfCurrentHour: () => Date;
/**
 * Adds specified number of minutes to a given date.
 * @param date Date to add minutes to
 * @param minutes number of minutes to add
 * @returns new Date with minutes added
 */
export declare const addMinutesToDate: (date: Date, minutes: number) => Date;
/**
 * Returns a timezone offset like "+05:30" or "-04:00"
 * Equivalent to Moment's `.format("Z")`
 */
export declare const getMomentStyleOffset: (timeZone: string, date?: Date) => string;
/**
 * Returns a short time zone abbreviation (like "PDT", "IST", etc.)
 * Equivalent to Moment's `.format("zz")`
 */
export declare const getTimeZoneAbbreviation: (timeZone: string, date?: Date) => string;
/**
 * Returns a list of all time zones.
 */
export declare const getTimeZoneNames: () => string[];
/**
 * Guesses the system time zone
 */
export declare const guessUserTimeZone: () => string;
/**
 * Formats a date using timezone-aware formatting
 * @param date Date to format
 * @param formatString Format string (e.g., "MMM d, yyyy hh:mm a")
 * @param timeZone Timezone to use (defaults to system timezone)
 * @returns Formatted date string
 */
export declare const formatInTimeZone: (date: Date | string | number, formatString: string, timeZone?: string) => string;
/**
 * Converts a date to a Date object using timezone-aware parsing
 * @param date Date to convert
 * @param timeZone Timezone to use (defaults to system timezone)
 * @returns Date object
 */
export declare const convertToDateInTimeZone: (date: Date | string | number, timeZone?: string) => Date;
/**
 * Parses a date string that is in a specific timezone and converts it to a Date object (UTC internally)
 * This is useful when you have a date string like "2024-10-17T15:30:00" that represents a time in a specific timezone
 * @param dateString Date string to parse
 * @param timeZone Timezone the date string is in
 * @returns Date object (stored as UTC internally)
 */
export declare const parseDateInTimeZone: (dateString: string, timeZone: string) => Date;
