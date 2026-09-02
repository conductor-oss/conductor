import { JsonSchema } from "@jsonforms/core";
/**
 * Validates that a given JSON Schema object is a valid draft-07 schema
 * that can be used with JsonForms.
 *
 * @returns true if valid, or an error message string if invalid/missing.
 */
export declare const isJSONSchemaValid: (jsonSchema: JsonSchema | undefined) => boolean | string;
