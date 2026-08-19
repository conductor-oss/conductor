import _lowerCase from "lodash/lowerCase";
import _upperFirst from "lodash/upperFirst";

import { findNextMissingSequentialNumber } from "utils/utils";

export const randomChars = (n = 7): string =>
  (Math.random() + 1).toString(36).substring(n);

export const getSequentiallySuffix = ({
  name,
  refNames,
}: {
  name: string;
  refNames: string[];
}) => {
  // Finding a suffix number array
  // Because the task name can be modified, so the suffix number maybe not sequential
  // ex: The original: [1,2,3]
  // after modifying it can be: [15,2,3]
  const taskNumbers = refNames.reduce((acc, taskReferenceName) => {
    if (taskReferenceName) {
      if (taskReferenceName.startsWith(`${name}`)) {
        const lastNumber = Number(taskReferenceName.replace(`${name}_`, ""));

        if (lastNumber > 0) {
          return [...acc, lastNumber];
        }

        return [...acc, 0];
      }
    }

    return acc;
  }, [] as number[]);

  let missingNum = findNextMissingSequentialNumber(taskNumbers);

  if (missingNum === null) {
    missingNum = taskNumbers[taskNumbers.length - 1] + 1;
  }

  const suffixString = missingNum ? `_${missingNum}` : "";

  return {
    name: `${name.replace("_ref", "")}${suffixString}`,
    taskReferenceName: `${name}${suffixString}`,
  };
};

export const toUpperFirst = (str: string) => _upperFirst(_lowerCase(str));

// Escape non-ASCII chars so a JSON string is ASCII-safe before HTTP transit.
// Some WAF/proxy deployments strip C1 bytes (0x80-0x9F) from the raw body,
// corrupting multibyte UTF-8 sequences (e.g. em-dash E2 80 94) and causing
// Jackson to throw "JSON parse error: Invalid UTF-8 middle byte".
export function asciiSafeJson(json: string): string {
  return json.replace(/[\u0080-\uFFFF]/g, (ch) =>
    `\\u${ch.charCodeAt(0).toString(16).padStart(4, "0")}`,
  );
}
