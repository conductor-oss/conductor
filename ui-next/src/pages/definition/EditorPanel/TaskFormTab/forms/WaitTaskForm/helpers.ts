import _isString from "lodash/isString";
import _isNil from "lodash/isNil";
import { WaitTaskDef } from "types";

const coerceToFullWordNotation = (el: string): string => {
  switch (el.trim()) {
    case "days":
    case "d":
      return "days";
    case "hours":
    case "hrs":
    case "h":
      return "hours";
    case "minutes":
    case "mins":
    case "m":
      return "minutes";
    case "seconds":
    case "secs":
    case "s":
      return "seconds";
  }
  return el.trim();
};

const defaultDurations: Array<[string, string]> = [
  ["", "days"],
  ["", "hours"],
  ["", "minutes"],
  ["", "seconds"],
];

export function durationStringToPairs(
  duration: string,
): Array<[string, string]> {
  if (_isString(duration)) {
    const durationArray = duration.split(/\s+/);

    if (duration.length > 0 && durationArray.length % 2 === 0) {
      return defaultDurations.map(([value, unit]) => {
        const validValueIndex = durationArray.findIndex(
          (v) => coerceToFullWordNotation(v) === unit,
        );
        const validValue =
          validValueIndex > 0 ? durationArray[validValueIndex - 1] : value;

        return [validValue, unit];
      });
    }

    return defaultDurations;
  }

  return defaultDurations;
}

export const detectWaitType = (task: WaitTaskDef) => {
  if (!_isNil(task?.inputParameters?.until)) {
    return "until";
  } else if (!_isNil(task?.inputParameters?.duration)) {
    return "duration";
  }
  return "signal";
};
