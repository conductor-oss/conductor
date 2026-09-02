import { HTTPMethods } from "types/TaskType";
export declare const LOCAL_STORAGE_KEY: {
    ROWS_PER_PAGE: string;
};
export declare const FORBIDDEN_DELETE_ERROR_MESSAGE = "You don't have permission to delete this resource.";
export declare const FORBIDDEN_PUT_ERROR_MESSAGE = "You don't have permission to update this resource.";
export declare const FORBIDDEN_POST_ERROR_MESSAGE = "You don't have permission to create this resource.";
export declare const FORBIDDEN_GET_ERROR_MESSAGE = "You don't have permission to view this resource.";
export declare const generateForbiddenMessage: (method: HTTPMethods) => "You don't have permission to delete this resource." | "You don't have permission to update this resource." | "You don't have permission to create this resource." | "You don't have permission to view this resource.";
/** User-facing message when a schedule name is already taken (strips API overwrite hint). */
export declare const formatScheduleNameConflictMessage: (message: string) => string;
/**
 * output: Feb 21, 2023 12:19 AM
 */
export declare const FORMAT_TIME_TO_LONG = "MMM d, yyyy hh:mm a";
/**
 * output: 2023-11-16 12:00 AM
 */
export declare const FORMAT_DATE_TIME_PICKER = "yyyy-MM-dd hh:mm aa";
export declare const SEARCH_QUERY_PARAM = "search";
export declare const PAGE_QUERY_PARAM = "page";
export declare const FILTER_QUERY_PARAM = "filter";
export declare const ACTIVE_FILTER_QUERY_PARAM = "activeFilter";
export declare const USER_ROLE_FILTER_QUERY_PARAM = "roleFilter";
export declare const HTTP_TEST_ENDPOINT = "https://orkes-api-tester.orkesconductor.com/api";
export declare const HOT_KEYS_SIDEBAR = "sidebar";
export declare const HOT_KEYS_WORKFLOW_DEFINITION = "workflow-definition";
export declare const TITLE_ALLOWED_CHARS = "^(?!-)[a-zA-Z0-9_-]*$";
export declare const ALPHANUMERIC_UNDERSCORE_HYPHEN_PATTERN = "^[a-zA-Z0-9_-]*$";
export declare const WORKFLOW_NAME_ERROR_MESSAGE = "The name should contain only letters (both uppercase and lowercase), digits, spaces, and the characters <, >, {, }, #, and -. No other special characters are allowed.";
export declare const TASK_NAME_ERROR_MESSAGE = "The name should contain only letters (both uppercase and lowercase), digits, spaces, and the characters <, >, {, }, #, and -. No other special characters are allowed.";
export declare const HEADER_Z_INDEX = 1000;
export declare const WORKFLOW_SEARCH_QUERY_SUGGESTIONS: string[];
export declare const TASK_SEARCH_QUERY_SUGGESTIONS: string[];
export declare enum ButtonPosition {
    TOP = "top",
    RIGHT = "right",
    BOTTOM = "bottom",
    LEFT = "left"
}
