import { JsonSchema } from "@jsonforms/core";
import { TagDto } from "types/Tag";
import { ErrorObj, TaskDef, TryFn } from "types/common";
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
export declare const GENERIC_ERROR = "Error performing action. error number:";
export declare const defaultGenericErrorHandler: (response: Response) => {
    message: string;
};
export declare const getErrors: (response: Response, genericErrorHandler?: (response: Response) => {
    message: string;
}) => Promise<any>;
export declare const getErrorMessage: (response: Response) => Promise<string>;
export declare const tryFunc: <T, E extends ErrorObj | undefined = undefined>({ fn, customError, showCustomError, }: {
    fn: TryFn<T>;
    customError?: E;
    showCustomError?: boolean;
}) => Promise<T>;
export declare const capitalizeFirstLetter: import("lodash/fp").LodashCapitalize;
export declare const humanizeStatus: (status?: string) => string;
export declare const getTitleSuffix: (type?: string, id?: string) => string;
export declare const tryToJson: <T>(str?: string | null) => T | undefined;
export declare const castToBooleanIfIsBooleanString: (value: string) => string | boolean;
export declare const isSafari: boolean;
/**
 * Convert time from seconds to d:h:m:s
 * Ex: 70 seconds = 1m 10s
 * @param timeInSeconds
 */
export declare const calculateTimeFromMillis: (timeInSeconds: number) => string;
export declare const calculateDifferentTime: (startTime: number, endTime: number) => string;
export declare const createSearchableTags: (tags: TagDto[]) => string;
export declare const totalPages: (currentPage: number, rowsPerPage: string, resultLength: string) => string;
/**
 * Finding the missing number sequentially
 * ex: array = [0,1,1,2,2,15]
 * expected: missingNum = 3
 * @param arr: number[]
 */
export declare const findNextMissingSequentialNumber: (arr: number[]) => number | null;
export declare const useCoerceToObject: (onChange: (a: string) => void, oValue: string | Record<string, unknown>) => [(val: string) => void, string, boolean];
export declare const optionsNameLabelGenerator: (options: string[]) => {
    name: string;
    label: string;
}[];
export declare const extractVariables: (text: string) => string[];
export declare const capitalizeEachWord: (text: string) => string;
export declare const isPseudoTask: (task: TaskDef) => boolean;
export declare const replaceNonAlphanumericWithUnderscore: (string: string) => string;
export declare const getCookie: (name: string) => string | null;
export declare const defaultValueFromSchema: (schema?: JsonSchema) => import("lodash").Dictionary<any>;
export declare const getBaseUrl: (url?: string) => string;
export declare const getInitials: (text: string, fallback?: string) => string;
