import { JsonSchema } from "@jsonforms/core";
import _capitalize from "lodash/fp/capitalize";
import _defaultTo from "lodash/fp/defaultTo";
import isEmpty from "lodash/isEmpty";
import isNil from "lodash/isNil";
import _mapValues from "lodash/mapValues";
import _pickBy from "lodash/pickBy";
import { useCallback, useState } from "react";
import { TagDto } from "types/Tag";
import {
  ErrorObj,
  FIELD_TYPE_OBJECT,
  TaskDef,
  TaskType,
  TryFn,
} from "types/common";
import { inferType } from "./helpers";
import { logger } from "./logger";

/**
 * When there are validation errors the backend will respond with something like:
 *
 * (2)
 * {
 *  "message" : "..."
 *  "validationErrors": [
 *   {
 *      "path": "ownerEmail",
 *      "message": "ownerEmail cannot be empty"
 *    }
 *  ]
 *  ...
 * }
 *
 * This function returns an object with the errors as properties e.g.:
 * { "ownerEmail": "ownerEmail cannot be empty" }
 * and the message if present.
 *
 * NOTES: path may take this form if it's a list registerTaskDef.taskDefinitions[0].ownerEmail.
 *
 * "message" may be a generic error message or a comma separated list of all messages.
 *
 * @param response Fetch response object
 * @returns if "errors" exists in the response an object which properties are the errors.
 */
export const GENERIC_ERROR = "Error performing action. error number:";

const defaultToEmpty = _defaultTo("");

export const defaultGenericErrorHandler = (response: Response) => ({
  message: `${GENERIC_ERROR} ${defaultToEmpty(
    String(response?.status),
  )} ${defaultToEmpty(response.statusText)}`,
});

export const getErrors = async (
  response: Response,
  genericErrorHandler = defaultGenericErrorHandler,
) => {
  const contentType = response.headers?.get("content-type");
  if (isNil(contentType) || contentType.indexOf("application/json") === -1) {
    console.error("Body is not even json. Check Response! ", response);
    return genericErrorHandler(response);
  }

  const clonedResponse = response.clone();
  const body = await clonedResponse.json();

  if (isEmpty(body?.validationErrors) && isEmpty(body?.message)) {
    console.error(
      Object.assign(Error("No error messages in response"), { body }),
    );
    return genericErrorHandler(clonedResponse);
  }

  return Object.assign(
    { message: body.message },
    ...(isEmpty(body.validationErrors)
      ? []
      : body.validationErrors.map(
          (error: { path: string; message: string }) => ({
            [error.path]: error.message,
          }),
        )),
  );
};

export const getErrorMessage = async (response: Response): Promise<string> => {
  const parsedError = await getErrors(response);

  if (parsedError?.message) {
    return parsedError?.message;
  }

  return "";
};

export const tryFunc = async <T, E extends ErrorObj | undefined = undefined>({
  fn,
  customError,
  showCustomError = true,
}: {
  fn: TryFn<T>;
  customError?: E;
  showCustomError?: boolean;
}) => {
  try {
    return await fn();
  } catch (error: any) {
    logger.error("[tryFunc] error:", error);
    const details = await getErrors(error);

    return Promise.reject({
      ...customError,
      originalError: details,
      message:
        !showCustomError && details?.message != null
          ? details?.message
          : customError?.message,
    });
  }
};

export const capitalizeFirstLetter = _capitalize;

export const getTitleSuffix = (type?: string, id?: string) => {
  if (type) {
    return ` - ${capitalizeFirstLetter(type)}` + (id ? ` - ${id}` : "");
  }
  return "";
};

export const tryToJson = <T>(str?: string | null): T | undefined => {
  if (str == null) {
    return undefined;
  }
  try {
    return JSON.parse(str) as T;
  } catch (error) {
    logger.error(`Error parsing JSON: ${error}`);
    return undefined;
  }
};

export const castToBooleanIfIsBooleanString = (value: string) => {
  if (value === "true") {
    return true;
  }

  if (value === "false") {
    return false;
  }

  return value;
};

export const isSafari =
  /Safari/.test(navigator.userAgent) && /Apple Computer/.test(navigator.vendor);

/**
 * Convert time from seconds to d:h:m:s
 * Ex: 70 seconds = 1m 10s
 * @param timeInSeconds
 */
export const calculateTimeFromMillis = (timeInSeconds: number) => {
  let totalTime = timeInSeconds >= 0 ? timeInSeconds : 0;
  const perDay = 24 * 60 * 60;
  const perHour = 60 * 60;
  const perMinute = 60;

  const days = Math.floor(totalTime / perDay);

  if (days > 0) {
    totalTime %= days * perDay;
  }

  const hours = Math.floor(totalTime / perHour);

  if (hours > 0) {
    totalTime %= hours * perHour;
  }

  const minutes = Math.floor(totalTime / perMinute);

  if (minutes > 0) {
    totalTime %= minutes * perMinute;
  }

  if (days === 0) {
    if (hours === 0) {
      return `${minutes}m ${totalTime}s`;
    } else {
      return `${hours}h ${minutes}m ${totalTime}s`;
    }
  }
  return `${days}d ${hours}h ${minutes}m ${totalTime}s`;
};

export const calculateDifferentTime = (startTime: number, endTime: number) => {
  if (endTime >= startTime) {
    const executionTime = endTime - startTime;

    if (executionTime < 1000) {
      return `${executionTime} ms`;
    }

    return calculateTimeFromMillis(Math.floor(executionTime / 1000));
  }

  return "";
};

export const createSearchableTags = (tags: TagDto[]) => {
  return (tags || []).map((tag) => `${tag.key}:${tag.value}`).join(" ");
};

export const totalPages = (
  currentPage: number,
  rowsPerPage: string,
  resultLength: string,
) => {
  let value = "";
  if (currentPage === 1 && resultLength < rowsPerPage) {
    value = "1";
  } else {
    value = "many";
  }
  return value;
};

/**
 * Finding the missing number sequentially
 * ex: array = [0,1,1,2,2,15]
 * expected: missingNum = 3
 * @param arr: number[]
 */
export const findNextMissingSequentialNumber = (arr: number[]) => {
  // Step 1: Sort the array
  arr.sort((a, b) => a - b);

  // Step 2: Loop through the sorted array and find the missing numbers
  let lastNumber = arr[0];
  let nextMissingNumber = null;

  for (let i = 1; i < arr.length; i++) {
    const currentNumber = arr[i];
    if (currentNumber !== lastNumber && currentNumber - lastNumber > 1) {
      nextMissingNumber = lastNumber + 1;
      break;
    }
    lastNumber = currentNumber;
  }

  // Step 3: Return the next missing number
  return nextMissingNumber;
};

export const useCoerceToObject = (
  onChange: (a: string) => void,
  oValue: string | Record<string, unknown>,
): [(val: string) => void, string, boolean] => {
  const [stringAsObject, setObjString] = useState<[string, boolean]>([
    inferType(oValue) === FIELD_TYPE_OBJECT
      ? JSON.stringify(oValue, null, 2)
      : "",
    false,
  ]);

  const handleUpdateObjectValue = useCallback(
    (val: string) => {
      try {
        const parsed = JSON.parse(val);
        onChange(parsed);
        setObjString([val, false]);
      } catch {
        setObjString([val, true]);
      }
    },
    [onChange],
  );
  return [handleUpdateObjectValue, ...stringAsObject];
};

export const optionsNameLabelGenerator = (options: string[]) => {
  const result: { name: string; label: string }[] = [];
  if (options && options.length > 0) {
    options.map((item) => {
      result.push({ name: item, label: item });
      return item;
    });
  }
  return result;
};

export const extractVariables = (text: string) => {
  const regex = /\$\{([^}]+)\}/g;

  const variables = [];
  let match;

  while ((match = regex.exec(text)) !== null) {
    variables.push(match[1]);
  }

  return variables;
};

export const capitalizeEachWord = (text: string) => {
  if (text.length > 1) {
    const words = text.split(" ");

    const capitalizedWords = words.map((word) => {
      return word.toLowerCase().charAt(0).toUpperCase() + word.slice(1);
    });

    return capitalizedWords.join(" ");
  } else {
    return text;
  }
};

export const isPseudoTask = (task: TaskDef) =>
  [TaskType.TERMINAL, TaskType.SWITCH_JOIN].includes(task.type);

export const replaceNonAlphanumericWithUnderscore = (string: string) => {
  return string.replace(/[^a-zA-Z0-9_]+/g, "_").replace(/^_+|_+$/g, "");
};

// Utility function to get cookie value
export const getCookie = (name: string): string | null => {
  const matches = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return matches ? decodeURIComponent(matches[1]) : null;
};

export const defaultValueFromSchema = (schema?: JsonSchema) => {
  if (!schema?.properties) {
    return {};
  }
  const defaultValues = _mapValues(
    schema.properties,
    (property) => property?.default,
  );
  const sanitizedDefaults = _pickBy(defaultValues, (val) => val !== undefined);
  return sanitizedDefaults;
};

export const getBaseUrl = (url = "") => {
  try {
    const parsedUrl = new URL(url);
    return `${parsedUrl?.protocol}//${parsedUrl?.hostname}${
      parsedUrl?.port ? `:${parsedUrl?.port}` : ""
    }`;
  } catch (error) {
    console.error("Invalid URL:", error);
    return "";
  }
};

export const getInitials = (text: string, fallback = "NA"): string => {
  if (!text) return fallback;

  const words = text
    ?.replace(/[_-]/g, " ") // Replace underscores and hyphens with spaces
    ?.replace(/([a-z])([A-Z])/g, "$1 $2") // Split camelCase (e.g., myText -> my Text)
    ?.split(" ")
    ?.filter(Boolean); // Remove empty strings

  if (words.length === 0) return fallback;

  // Handle single word case
  if (words.length === 1) {
    const word = words[0].trim();
    return word.length >= 2
      ? word.substring(0, 2).toUpperCase()
      : (word[0]?.toUpperCase() || fallback[0]) + (fallback[1] || "");
  }

  // Handle multiple words
  const initials = words
    ?.slice(0, 2)
    ?.map((word) => word[0]?.toUpperCase() || "")
    ?.join("");

  return initials || fallback;
};
