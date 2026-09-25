import {
  SCHEDULE_NAME_INVALID,
  SCHEDULE_NAME_REQUIRED,
  validateScheduleName,
  validateScheduleNameRequired,
} from "../utils/scheduleValidation";

describe("validateScheduleNameRequired", () => {
  it.each([undefined, null, "", "   ", "\t\n"])(
    "returns the required error for %j",
    (name) => {
      expect(validateScheduleNameRequired(name)).toBe(SCHEDULE_NAME_REQUIRED);
    },
  );

  it.each(["my_schedule", "legacy-name", "has space"])(
    "accepts non-blank name %j without checking characters",
    (name) => {
      expect(validateScheduleNameRequired(name)).toBeNull();
    },
  );
});

describe("validateScheduleName", () => {
  it.each([undefined, null, "", "   "])(
    "returns the required error for %j",
    (name) => {
      expect(validateScheduleName(name)).toBe(SCHEDULE_NAME_REQUIRED);
    },
  );

  it.each(["my-schedule", "has space", "dot.name"])(
    "returns the invalid-characters error for %j",
    (name) => {
      expect(validateScheduleName(name)).toBe(SCHEDULE_NAME_INVALID);
    },
  );

  it.each(["my_schedule", "Schedule1", "_1"])("accepts %j", (name) => {
    expect(validateScheduleName(name)).toBeNull();
  });
});
