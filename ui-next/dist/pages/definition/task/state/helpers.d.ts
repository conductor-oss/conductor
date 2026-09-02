import type { ErrorObject } from "ajv";
export declare const TASK_DEFINITION_SAVED_SUCCESSFULLY_MESSAGE = "Task definition saved successfully.";
export declare const TASK_FORM_MACHINE_ID = "taskDefinitionFormMachine";
export declare const TASK_DIALOGS_MACHINE_ID = "taskDefinitionDialogsMachine";
/**
 * Parse errors (array) to object
 * @param errors
 */
export declare const parseErrors: (errors: ErrorObject[] | null) => {};
