import { JsonSchema } from "@jsonforms/core";
import Ajv from "ajv";
import addFormats from "ajv-formats";

const ajv = new Ajv();
addFormats(ajv);

/**
 * Validates that a given JSON Schema object is a valid draft-07 schema
 * that can be used with JsonForms.
 *
 * @returns true if valid, or an error message string if invalid/missing.
 */
export const isJSONSchemaValid = (
  jsonSchema: JsonSchema | undefined,
): boolean | string => {
  if (!jsonSchema) {
    return false;
  }
  try {
    ajv.validateSchema(jsonSchema, true);
    return true;
  } catch (e: any) {
    return e.message;
  }
};
