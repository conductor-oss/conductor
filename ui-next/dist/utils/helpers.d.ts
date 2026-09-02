import { HumanTaskState as TaskState } from "types/HumanTaskTypes";
import { WorkflowExecutionStatus } from "types/Execution";
import { TaskStatus } from "types/TaskStatus";
import { FieldType } from "types/common";
export declare function isFailedTask(status: TaskStatus): status is TaskStatus.FAILED | TaskStatus.FAILED_WITH_TERMINAL_ERROR | TaskStatus.CANCELED | TaskStatus.TIMED_OUT;
/**
 * Create data table title via search result
 * @param {array} filteredData: data after filtering or searching
 * @param {array} data: data of table
 * @returns {string}
 */
/**
 * Formats a result count with a correctly singularized noun, e.g.
 * "1 result" / "3 results".
 */
export declare function pluralizeResults(count: number): string;
export declare function createTableTitle({ filteredData, data, }: {
    filteredData: any[];
    data: any[];
}): string;
export declare function juxt<T extends readonly unknown[]>(...fns: readonly ((...args: T) => unknown)[]): (...args: T) => unknown[];
/**
 * Download file
 * @param {object} data
 * @param {string} fileName
 * @param {string} type
 */
export type ExportableObject = {
    data: Record<string, unknown>;
    fileName: string;
    type: string;
};
export declare const exportObjToFile: ({ data, fileName, type, }: ExportableObject) => void;
/**
 * Get color for rendering chip status
 * @param {string} status: item's status (ex: workflow's status...)
 * @returns {string}
 */
export declare const getChipStatusColor: (status: TaskStatus | WorkflowExecutionStatus | TaskState) => "#9FDCAA" | "#8DE0F9" | "#FBB4C6" | "#FCD181";
/**
 * Open link in new tab
 * @param {string} url
 */
export declare const openInNewTab: (url: string) => void;
export declare const inferType: (value: any) => FieldType;
export type ValueInputDefaultValues = Partial<Record<FieldType, unknown>>;
export declare const DEFAULT_FIELD_VALUES_CONF: Record<FieldType, unknown>;
export declare const castToType: (value: any, type: FieldType, defaultValuesProvided?: ValueInputDefaultValues) => any;
export declare const checkCoerceTypeError: ({ value, coerceTo, }: {
    value: any;
    coerceTo: any;
}) => boolean;
export declare function replacePathPlaceholdersToWorkflowInput(path: string): string;
export declare const parseErrorResponse: ({ response, module, operation, }: {
    response: Response;
    module: string;
    operation?: string;
}) => Promise<string>;
