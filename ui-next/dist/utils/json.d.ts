export declare const extractVariablesFromJSON: (data: Record<string, unknown>) => Record<string, unknown>;
/**
 * Downgrades a JSON schema from newer versions (Draft 2019-09, Draft 2020-12) to Draft 7.
 * This function:
 * - Replaces $schema URI with Draft 7 URI
 * - Converts $defs to definitions
 * - Removes unsupported keywords (unevaluatedProperties, unevaluatedItems, etc.)
 *
 * @param schema - The JSON schema to downgrade
 * @returns A downgraded schema compatible with Draft 7, or an empty object if input is invalid
 */
export declare const downgradeSchemaToDraft7: (schema: Record<string, any>) => Record<string, any>;
