import { ScheduleType } from "../Schedule";
/**
 * Parse JSON string safely, returning null for empty strings
 */
export declare function JSONParse(text: string): any;
/**
 * Convert date field to timestamp value
 */
export declare function getDateFromField(d1: string | number | Date): number | "";
/**
 * Convert form data to code representation
 */
export declare function formToCodeData(scheduleState: ScheduleType, schedule: any): Partial<ScheduleType> | null;
/**
 * Convert code data to form representation
 */
export declare function codeToFormData(data: string, scheduleState: ScheduleType): ScheduleType;
