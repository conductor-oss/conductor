import type { ErrorObject } from "ajv";
import _set from "lodash/set";

export const TASK_DEFINITION_SAVED_SUCCESSFULLY_MESSAGE =
  "Task definition saved successfully.";

export const TASK_FORM_MACHINE_ID = "taskDefinitionFormMachine";
export const TASK_DIALOGS_MACHINE_ID = "taskDefinitionDialogsMachine";

/**
 * Parse errors (array) to object
 * @param errors
 */
export const parseErrors = (errors: ErrorObject[] | null) =>
  errors
    ? errors.reduce(
        (obj, { instancePath, schemaPath, params, keyword, message }) => {
          const keys = instancePath.split("/") as string[];

          if (keyword === "required" && params?.missingProperty) {
            keys.push(params.missingProperty);
          }

          // Remove the 1st empty ("") item in the array
          keys.shift();

          const errorKey = keys.at(-1);

          if (errorKey) {
            if (
              !schemaPath.startsWith("#/") &&
              keys.length === 1 &&
              keyword === "type"
            ) {
              keys.push(keyword);
            }

            return _set(obj, keys.join("."), {
              message,
            });
          }

          // Checking unique items in array (bulk mode)
          if (keyword === "uniqueItems") {
            return {
              ...obj,
              misc: {
                uniqueItems: { message },
              },
            };
          }

          if (keyword) {
            return _set(
              obj,
              params.type === "array" ? `misc.${keyword}` : keyword,
              {
                message,
              },
            );
          }

          return obj;
        },
        {},
      )
    : {};
