import _isArray from "lodash/isArray";
import _isNull from "lodash/isNull";
import _isObject from "lodash/isObject";
import _mapValues from "lodash/mapValues";
import _omitBy from "lodash/omitBy";
import { FieldErrors, FieldValues } from "react-hook-form";

export const getEditorToFormValue = (
  formValues: FieldValues,
  editorValue: FieldValues,
  hiddenKeys: string[] = [],
): any => {
  const result: FieldValues = {};

  // Preserve the order of keys from obj2
  const editorValueKeys = Object.keys(editorValue);

  // Merge keys from both objects, but prioritize editorValue order
  const mergedKeys = Array.from(
    new Set([...editorValueKeys, ...Object.keys(formValues)]),
  );

  // Iterate over merged keys
  mergedKeys.forEach((key) => {
    // Check if key is not in hiddenKeys array
    if (!hiddenKeys.includes(key)) {
      // If key exists in editorValue
      if (Object.prototype.hasOwnProperty.call(editorValue, key)) {
        // If the value is null or undefined, handle accordingly
        if (editorValue[key] === null || editorValue[key] === undefined) {
          result[key] = editorValue[key];
        } else if (
          typeof editorValue[key] === "object" &&
          !Array.isArray(editorValue[key])
        ) {
          // If the value is an object, recursively update
          result[key] = getEditorToFormValue(
            formValues[key] || {},
            editorValue[key],
            hiddenKeys,
          );
        } else if (Array.isArray(editorValue[key])) {
          // If the value is an array, handle each element
          result[key] = editorValue[key].map((item: any, index: number) => {
            // If the element is an object, recursively update
            if (typeof item === "object" && !Array.isArray(item)) {
              return getEditorToFormValue(
                formValues[key]?.[index] || {},
                item,
                hiddenKeys,
              );
            }
            // Otherwise, return the element
            return item;
          });
        } else {
          // Otherwise, assign the value directly
          result[key] = editorValue[key];
        }
      } else {
        // If key does not exist in editorValue, set null
        result[key] = null;
      }
    } else {
      // Otherwise, keep the value from formValues
      result[key] = formValues[key];
    }
  });

  return result;
};

export const getFormToEditorValue = (
  formValues: FieldValues,
  hiddenKeys: string[] = [],
) => {
  return JSON.stringify(
    removeNullAndHiddenKeys(formValues, hiddenKeys),
    null,
    2,
  );
};

export const getReactHookFormError = <T extends FieldValues>(
  errors: FieldErrors<T>,
): string | null => {
  for (const key in errors) {
    const error = errors[key];
    if (typeof error === "object" && error !== null) {
      const errorMessage = getReactHookFormError(error as FieldErrors<T>);
      if (errorMessage) {
        return errorMessage;
      }
    } else if (typeof error === "string") {
      return error;
    }
  }
  return null;
};

export const removeNullAndHiddenKeys = (
  value: any,
  hiddenKeys: string[] = [],
): object => {
  if (_isArray(value)) {
    return value
      .map((val) => removeNullAndHiddenKeys(val, hiddenKeys))
      .filter(Boolean);
  }
  if (_isObject(value)) {
    return _omitBy(
      _mapValues(value, (value) => removeNullAndHiddenKeys(value, hiddenKeys)),
      (value, key) => _isNull(value) || hiddenKeys?.includes(key),
    );
  }

  return value;
};
