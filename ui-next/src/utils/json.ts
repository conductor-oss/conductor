import cloneDeep from "lodash/cloneDeep";

const VARIABLE_REGEX = /\$\{([^}]+)\}/g;

export const extractVariablesFromJSON = (data: Record<string, unknown>) => {
  const extractedVariables: Record<string, unknown> = {};

  const processObject = (obj: Record<string, unknown>, path = ""): void => {
    for (const [key, value] of Object.entries(obj)) {
      if (typeof value === "string") {
        let match;
        while ((match = VARIABLE_REGEX.exec(value)) !== null) {
          const variableName = match[1];
          const keyName =
            path !== "" ? `${path.replace(/^\./, "")}.${key}` : key;
          extractedVariables[keyName] = variableName;
        }
      } else if (typeof value === "object" && value !== null) {
        processObject(value as Record<string, unknown>, `${path}.${key}`);
      }
    }
  };

  processObject(data);

  return extractedVariables;
};

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
export const downgradeSchemaToDraft7 = (
  schema: Record<string, any>,
): Record<string, any> => {
  // Defensive check: handle null, undefined, non-objects, and arrays
  if (schema == null || typeof schema !== "object" || Array.isArray(schema)) {
    // Return empty object for invalid inputs to maintain type safety
    if (schema == null || typeof schema !== "object") {
      return {};
    }
    // Return as-is for arrays (they might be valid in some contexts, but not as root schema)
    return schema;
  }

  // Recursively check nested schema objects
  const nestedKeys = [
    "properties",
    "items",
    "additionalProperties",
    "patternProperties",
    "allOf",
    "anyOf",
    "oneOf",
    "not",
    "if",
    "then",
    "else",
    "definitions",
    "$defs",
  ] as const;

  const NEWER_KEYWORDS = new Set([
    "$defs",
    "unevaluatedProperties",
    "unevaluatedItems",
    "dependentRequired",
    "dependentSchemas",
    "$anchor",
    "$dynamicAnchor",
    "$dynamicRef",
    "minContains",
    "maxContains",
  ] as const);

  // Recursively check if schema has newer keywords anywhere (including nested)
  const hasNewerKeywordsRecursive = (obj: any): boolean => {
    // Defensive checks: handle null, undefined, non-objects
    if (obj == null || typeof obj !== "object") {
      return false;
    }

    // Handle arrays with defensive checks
    if (Array.isArray(obj)) {
      // Check for empty arrays
      if (obj.length === 0) {
        return false;
      }
      // Safely iterate through array items
      try {
        return obj.some((item) => {
          try {
            return hasNewerKeywordsRecursive(item);
          } catch {
            // If processing an item fails, continue checking other items
            return false;
          }
        });
      } catch {
        // If array iteration fails, assume no newer keywords
        return false;
      }
    }

    // Check for newer keywords at current level
    try {
      for (const key of NEWER_KEYWORDS) {
        if (obj != null && key in obj) {
          return true;
        }
      }
    } catch {
      // If keyword checking fails, continue to nested checks
    }

    // Check nested schema objects
    try {
      for (const key of nestedKeys) {
        if (obj == null || !(key in obj)) {
          continue;
        }

        const value = obj[key];

        // Skip if value is null, undefined, or empty string
        if (value == null || value === "") {
          continue;
        }

        if (Array.isArray(value)) {
          // Handle empty arrays
          if (value.length === 0) {
            continue;
          }
          // Safely check array items
          try {
            if (value.some((item) => hasNewerKeywordsRecursive(item))) {
              return true;
            }
          } catch {
            // Continue checking other nested keys if this fails
            continue;
          }
        } else if (typeof value === "object" && value !== null) {
          if (
            key === "properties" ||
            key === "patternProperties" ||
            key === "definitions" ||
            key === "$defs"
          ) {
            // These are objects with schema values
            // Defensive check: ensure value is a proper object
            if (
              value == null ||
              typeof value !== "object" ||
              Array.isArray(value)
            ) {
              continue;
            }

            try {
              for (const nestedKey in value) {
                // Check if property exists and is own property
                if (!Object.prototype.hasOwnProperty.call(value, nestedKey)) {
                  continue;
                }

                const nestedValue = value[nestedKey];
                // Skip null/undefined nested values
                if (nestedValue == null) {
                  continue;
                }

                if (hasNewerKeywordsRecursive(nestedValue)) {
                  return true;
                }
              }
            } catch {
              // If iteration fails, continue checking other keys
              continue;
            }
          } else {
            // Recursively check other object values
            try {
              if (hasNewerKeywordsRecursive(value)) {
                return true;
              }
            } catch {
              // Continue if recursive check fails
              continue;
            }
          }
        }
      }
    } catch {
      // If nested checking fails, assume no newer keywords
      return false;
    }

    return false;
  };

  // Check if schema version needs conversion to Draft 7
  // JsonForms only supports Draft 7, so we need to convert Draft 04, 06, and newer versions
  // Also ensure $schema is always set to Draft 7 for JsonForms compatibility
  const schemaVersion = schema?.$schema;
  const isDraft07 =
    schemaVersion != null &&
    typeof schemaVersion === "string" &&
    schemaVersion.length > 0 &&
    schemaVersion.includes("draft-07");

  // Check if schema has HTTPS URI which needs to be converted to HTTP
  // Ajv tries to fetch HTTPS URIs, causing errors
  const hasHttpsSchemaUri =
    schemaVersion != null &&
    typeof schemaVersion === "string" &&
    schemaVersion.startsWith("https://");

  // Check if downgrading/conversion is necessary before cloning
  try {
    // Only return early if already Draft 7 with HTTP (not HTTPS) URI and no newer keywords
    // If $schema is missing or is HTTPS, we need to process it
    if (isDraft07 && !hasHttpsSchemaUri && !hasNewerKeywordsRecursive(schema)) {
      // Already Draft 7 with HTTP URI and no newer keywords to convert anywhere
      return schema;
    }
  } catch {
    // If checking fails, proceed with downgrading to be safe
  }

  // Create a deep copy to avoid mutating the original (only if downgrading is needed)
  // Try structuredClone first (native browser API), fallback to lodash cloneDeep
  let downgraded: Record<string, any>;
  try {
    downgraded =
      typeof structuredClone !== "undefined"
        ? structuredClone(schema)
        : cloneDeep(schema);

    // Defensive check: ensure cloning succeeded
    if (
      downgraded == null ||
      typeof downgraded !== "object" ||
      Array.isArray(downgraded)
    ) {
      // If cloning failed, return original schema or empty object
      return schema != null &&
        typeof schema === "object" &&
        !Array.isArray(schema)
        ? schema
        : {};
    }
  } catch {
    // If cloning fails (e.g., circular references), return original schema
    // or empty object as fallback
    return schema != null &&
      typeof schema === "object" &&
      !Array.isArray(schema)
      ? schema
      : {};
  }

  // Replace $schema with Draft 7 URI (without HTTPS to avoid external fetch issues)
  // JsonForms/Ajv will try to fetch HTTPS URIs, causing errors
  try {
    // Use HTTP instead of HTTPS to prevent Ajv from trying to fetch the schema
    // Both URIs are equivalent for JSON Schema Draft 7, but HTTP prevents validation errors
    downgraded.$schema = "http://json-schema.org/draft-07/schema#";
  } catch {
    // If setting $schema fails, continue processing
  }

  // Recursively process the schema
  const processSchema = (objA: any): any => {
    // Defensive check: handle null, undefined, and non-objects
    if (objA == null) {
      return objA;
    }

    if (typeof objA !== "object") {
      return objA;
    }

    // Handle arrays - process each element
    if (Array.isArray(objA)) {
      // Handle empty arrays
      if (objA.length === 0) {
        return objA;
      }

      try {
        return objA.map((item) => {
          try {
            return processSchema(item);
          } catch {
            // If processing an item fails, return it as-is
            return item;
          }
        });
      } catch {
        // If mapping fails, return array as-is
        return objA;
      }
    }

    // Create a shallow copy for processing
    let obj: Record<string, any>;
    try {
      obj = { ...objA };
    } catch {
      // If spreading fails, use original object
      obj = objA;
    }

    // Defensive check: ensure obj is still a valid object
    if (obj == null || typeof obj !== "object" || Array.isArray(obj)) {
      return obj;
    }

    // Convert $defs to definitions
    let definitionsProcessed = false;
    try {
      if (
        obj.$defs != null &&
        typeof obj.$defs === "object" &&
        !Array.isArray(obj.$defs)
      ) {
        // Merge $defs into existing definitions if it exists, otherwise create it
        if (
          obj.definitions != null &&
          typeof obj.definitions === "object" &&
          !Array.isArray(obj.definitions)
        ) {
          // Merge $defs into definitions, with $defs taking precedence for duplicate keys
          try {
            obj.definitions = { ...obj.definitions, ...obj.$defs };
          } catch {
            // If merging fails, just use $defs
            obj.definitions = obj.$defs;
          }
        } else {
          obj.definitions = obj.$defs;
        }

        delete obj.$defs;

        // Recursively process definitions
        if (
          obj.definitions != null &&
          typeof obj.definitions === "object" &&
          !Array.isArray(obj.definitions)
        ) {
          try {
            for (const key in obj.definitions) {
              if (Object.prototype.hasOwnProperty.call(obj.definitions, key)) {
                const defValue = obj.definitions[key];
                if (defValue != null) {
                  obj.definitions[key] = processSchema(defValue);
                }
              }
            }
            definitionsProcessed = true;
          } catch {
            // If processing definitions fails, continue
            definitionsProcessed = true;
          }
        }
      }
    } catch {
      // If $defs processing fails, continue with other processing
    }

    // Update $ref values that reference $defs to use definitions instead
    try {
      if (
        obj.$ref != null &&
        typeof obj.$ref === "string" &&
        obj.$ref.length > 0
      ) {
        // Replace #/$defs/ with #/definitions/
        obj.$ref = obj.$ref.replace(/^#\/\$defs\//, "#/definitions/");

        // Remove external HTTP(S) references that JsonForms can't resolve
        // This fixes the error: "no schema with key or ref https://json-schema.org/draft-07/schema#"
        if (obj.$ref.startsWith("http://") || obj.$ref.startsWith("https://")) {
          delete obj.$ref;
        }
      }
    } catch {
      // If $ref processing fails, continue
    }

    // Remove unsupported keywords
    const unsupportedKeywords = [
      "unevaluatedProperties",
      "unevaluatedItems",
      "dependentRequired",
      "dependentSchemas",
      "$anchor",
      "$dynamicAnchor",
      "$dynamicRef",
      "minContains",
      "maxContains",
    ] as const;

    try {
      for (const keyword of unsupportedKeywords) {
        if (obj != null && keyword in obj) {
          try {
            delete obj[keyword];
          } catch {
            // If deletion fails, continue with other keywords
            continue;
          }
        }
      }
    } catch {
      // If keyword removal fails, continue with processing
    }

    // Process nested schema objects
    // Configuration for different schema key processing strategies
    const schemaProcessors: Record<
      string,
      {
        type: "object" | "array" | "arrayOrSingle" | "single";
        condition?: (value: any) => boolean;
      }
    > = {
      properties: { type: "object" },
      patternProperties: { type: "object" },
      items: { type: "arrayOrSingle" },
      additionalProperties: {
        type: "single",
        condition: (value) =>
          value != null && typeof value === "object" && !Array.isArray(value),
      },
      allOf: { type: "array" },
      anyOf: { type: "array" },
      oneOf: { type: "array" },
      not: { type: "single" },
      if: { type: "single" },
      then: { type: "single" },
      else: { type: "single" },
      definitions: {
        type: "object",
        condition: () => !definitionsProcessed,
      },
    };

    try {
      for (const [key, processor] of Object.entries(schemaProcessors)) {
        if (obj == null) {
          break;
        }

        const value = obj[key];

        // Skip null, undefined, and empty strings
        if (value === undefined || value === null || value === "") {
          continue;
        }

        // Check condition if provided
        try {
          if (processor.condition && !processor.condition(value)) {
            continue;
          }
        } catch {
          // If condition check fails, skip this processor
          continue;
        }

        try {
          switch (processor.type) {
            case "object":
              // Process object with schema values (properties, patternProperties, definitions)
              if (
                value != null &&
                typeof value === "object" &&
                !Array.isArray(value)
              ) {
                try {
                  for (const nestedKey in value) {
                    if (
                      Object.prototype.hasOwnProperty.call(value, nestedKey)
                    ) {
                      const nestedValue = value[nestedKey];
                      if (nestedValue != null) {
                        value[nestedKey] = processSchema(nestedValue);
                      }
                    }
                  }
                } catch {
                  // If processing object properties fails, continue
                }
              }
              break;

            case "array":
              // Process array of schemas
              if (Array.isArray(value)) {
                if (value.length > 0) {
                  try {
                    obj[key] = value.map((item: any) => {
                      try {
                        return processSchema(item);
                      } catch {
                        return item;
                      }
                    });
                  } catch {
                    // If mapping fails, leave array as-is
                  }
                }
              }
              break;

            case "arrayOrSingle":
              // Process items which can be array or single schema
              if (Array.isArray(value)) {
                if (value.length > 0) {
                  try {
                    obj[key] = value.map((item: any) => {
                      try {
                        return processSchema(item);
                      } catch {
                        return item;
                      }
                    });
                  } catch {
                    // If mapping fails, leave array as-is
                  }
                }
              } else if (value != null) {
                try {
                  obj[key] = processSchema(value);
                } catch {
                  // If processing fails, leave value as-is
                }
              }
              break;

            case "single":
              // Process single schema value
              if (value != null) {
                try {
                  obj[key] = processSchema(value);
                } catch {
                  // If processing fails, leave value as-is
                }
              }
              break;
          }
        } catch {
          // If processing this key fails, continue with other keys
          continue;
        }
      }
    } catch {
      // If schema processing fails, return what we have so far
    }

    return obj;
  };

  try {
    return processSchema(downgraded);
  } catch {
    // If final processing fails, return the cloned schema or original as fallback
    return downgraded != null &&
      typeof downgraded === "object" &&
      !Array.isArray(downgraded)
      ? downgraded
      : schema != null && typeof schema === "object" && !Array.isArray(schema)
        ? schema
        : {};
  }
};
