import _isPlainObject from "lodash/isPlainObject";
import _isArray from "lodash/isArray";

export const replaceValues = (
  obj: Record<string | number, unknown>,
  value: string | number,
  newValue: string | number,
) => {
  const arrayReplacer = (iv: unknown): unknown => {
    if (typeof iv === "string" || typeof iv === "number") {
      return iv === value ? newValue : iv;
    } else if (_isPlainObject(iv)) {
      return replaceValues(
        iv as Record<string | number, unknown>,
        value,
        newValue,
      );
    } else if (_isArray(iv)) {
      return iv.map(arrayReplacer);
    }
    return iv;
  };

  return Object.fromEntries(
    Object.entries(obj).map(([key, val]): [string | number, unknown] => {
      if (_isPlainObject(val)) {
        return [
          key,
          replaceValues(
            val as Record<string | number, unknown>,
            value,
            newValue,
          ),
        ];
      } else if (_isArray(val)) {
        return [key, val.map(arrayReplacer)];
      } else if (val === value) {
        return [key, newValue];
      }
      return [key, val];
    }),
  );
};
export const flipObject = (obj: Record<string | number, string | number>) =>
  Object.fromEntries(Object.entries(obj).map((a) => a.reverse()));

export const isObjectOrArray = (value: any): boolean =>
  (typeof value === "object" && value !== null) || Array.isArray(value);

export const isObjectOnlyNotArray = (value: any): boolean =>
  typeof value === "object" && value !== null && !Array.isArray(value);
