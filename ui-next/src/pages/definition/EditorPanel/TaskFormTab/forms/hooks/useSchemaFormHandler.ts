import { useCallback } from "react";
import { assoc as _assoc, pipe as _pipe } from "lodash/fp";
import { getAuthHeaders } from "shared/auth/tokenManagerJotai";
import { getInputParametersFromSchemaIfNeeded } from "../../helpers";
import { SchemaFormPropsValue } from "../SchemaForm";
import { TaskFormProps } from "../types";

/**
 * Checks if two values have the same type, handling special cases like arrays and null
 */
const typesMatch = (existingValue: unknown, defaultValue: unknown): boolean => {
  const existingType = typeof existingValue;
  const defaultType = typeof defaultValue;

  // Handle null separately (typeof null is "object" in JavaScript)
  if (existingValue === null && defaultValue === null) return true;
  if (existingValue === null || defaultValue === null) return false;

  // Handle arrays separately (typeof array is "object" in JavaScript)
  const existingIsArray = Array.isArray(existingValue);
  const defaultIsArray = Array.isArray(defaultValue);
  if (existingIsArray && defaultIsArray) return true;
  if (existingIsArray || defaultIsArray) return false;

  // For primitive types, compare directly
  if (existingType !== defaultType) return false;

  // Both are objects (but not arrays or null)
  if (existingType === "object") {
    // For objects, we consider them matching if both are objects
    // (we don't do deep comparison of object structure)
    return true;
  }

  return true;
};

/**
 * Custom hook that handles schema form changes and automatically populates
 * inputParameters from schema defaults when appropriate.
 *
 * @param props - TaskFormProps containing task and onChange
 * @returns A handler function for SchemaForm onChange events
 */
export const useSchemaFormHandler = ({ task, onChange }: TaskFormProps) => {
  const handleSchemaChange = useCallback(
    async (schema?: SchemaFormPropsValue) => {
      const updatedTask = _pipe(
        _assoc("taskDefinition.inputSchema", schema?.inputSchema),
        _assoc("taskDefinition.outputSchema", schema?.outputSchema),
        _assoc("taskDefinition.enforceSchema", schema?.enforceSchema),
      )(task);

      const authHeaders = getAuthHeaders();
      const defaultValues = await getInputParametersFromSchemaIfNeeded(
        schema,
        task,
        authHeaders,
      );

      if (defaultValues) {
        const existingParams = updatedTask.inputParameters || {};
        const mergedParams: Record<string, unknown> = { ...defaultValues };

        // Preserve existing parameters that have valid values and matching types
        for (const [key, value] of Object.entries(existingParams)) {
          const defaultValue = defaultValues[key];
          const valueType = typeof value;

          // If parameter exists in schema defaults, check type compatibility
          if (defaultValue !== undefined) {
            // If types don't match, use default value (don't preserve existing)
            if (!typesMatch(value, defaultValue)) {
              // Type mismatch - use default value (already in mergedParams)
              continue;
            }
          }

          // Check if value is valid (should be kept)
          if (valueType === "number") {
            // For numbers, keep if not 0
            if (value !== 0) {
              mergedParams[key] = value;
            }
          } else if (valueType === "boolean") {
            // For booleans, always keep (we can't determine intent)
            mergedParams[key] = value;
          } else if (valueType === "string") {
            // For strings, keep if not blank
            const stringValue = value as string;
            if (stringValue !== "" && stringValue?.trim() !== "") {
              mergedParams[key] = value;
            }
          } else if (value != null) {
            // For other types (objects, arrays, etc.), keep if not null/undefined
            mergedParams[key] = value;
          }
          // If value is null, undefined, empty string, or 0, it's removed (not added to mergedParams)
        }

        updatedTask.inputParameters = mergedParams;
      }

      onChange(updatedTask);
    },
    [task, onChange],
  );

  return handleSchemaChange;
};
