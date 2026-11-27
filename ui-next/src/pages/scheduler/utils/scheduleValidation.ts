export const SCHEDULE_NAME_REQUIRED = "Name is required";
export const SCHEDULE_NAME_INVALID =
  "Name can only contain letters, numbers, and underscores.";

const scheduleNamePattern = /^[a-zA-Z0-9_]+$/;

/**
 * Returns an error message if the schedule name is missing or blank, otherwise null.
 */
export function validateScheduleNameRequired(
  name: string | null | undefined,
): string | null {
  return name?.trim() ? null : SCHEDULE_NAME_REQUIRED;
}

/**
 * Returns an error message if the schedule name is missing or has invalid
 * characters, otherwise null.
 */
export function validateScheduleName(
  name: string | null | undefined,
): string | null {
  const requiredError = validateScheduleNameRequired(name);
  if (requiredError) {
    return requiredError;
  }
  return scheduleNamePattern.test(name as string)
    ? null
    : SCHEDULE_NAME_INVALID;
}
