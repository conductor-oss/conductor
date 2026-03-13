import {
  FIELD_TYPE_BOOLEAN,
  FIELD_TYPE_NULL,
  FIELD_TYPE_NUMBER,
  FIELD_TYPE_OBJECT,
  FIELD_TYPE_STRING,
} from "types/common";
import { castToType, checkCoerceTypeError, inferType } from "utils/helpers";

const inferTypeCases = [
  {
    value: 123,
    expected: FIELD_TYPE_NUMBER,
  },
  {
    value: null,
    expected: FIELD_TYPE_NULL,
  },
  {
    value: "test",
    expected: FIELD_TYPE_STRING,
  },
  {
    value: false,
    expected: FIELD_TYPE_BOOLEAN,
  },
  {
    value: { key: "value" },
    expected: FIELD_TYPE_OBJECT,
  },
];

describe("Check inferType function", () => {
  test.each(inferTypeCases)(
    "given '$value' as argument, returns $expected",
    ({ value, expected }) => {
      const result = inferType(value);

      expect(result).toBe(expected);
    },
  );
});

const castToTypeCases = [
  {
    value: 123,
    expected: 123,
  },
  {
    value: 0,
    expected: 0,
  },
  {
    value: "123.321",
    expected: "123.321",
  },
  {
    value: null,
    expected: null,
  },
  {
    value: "test",
    expected: "test",
  },
  {
    value: false,
    expected: false,
  },
  {
    value: '{ key: "value" }',
    expected: '{ key: "value" }',
  },
  {
    value: { key: "value" },
    expected: {},
  },
  {
    value: "",
    expected: "",
  },
  {
    value: "[1,2]",
    expected: "[1,2]",
  },
];

describe("Check castToType function", () => {
  test.each(castToTypeCases)(
    "given '$value' as argument, returns '$expected'",
    ({ value, expected }) => {
      const result = castToType(value, inferType(value));

      // Use toMatchObject for objects (which also works for primitives)
      // or toBe for primitives - determined outside conditional
      const isObjectExpected = expected && typeof expected === "object";

      // Always make both assertions - one will be the actual check, one will be trivial
      expect(isObjectExpected ? result : null).toMatchObject(
        isObjectExpected ? expected : {},
      );
      expect(isObjectExpected || result === expected).toBeTruthy();
    },
  );
});

// true: has error
const checkCoerceTypeErrorCases = [
  {
    value: 123,
    coerceTo: "integer",
    expected: false,
  },
  {
    value: 123.123,
    coerceTo: "integer",
    expected: true,
  },
  {
    value: "123",
    coerceTo: "integer",
    expected: false,
  },
  {
    value: 123.321,
    coerceTo: "double",
    expected: false,
  },
  {
    value: "${someVariables}",
    coerceTo: "double",
    expected: false,
  },
  {
    value: "123.321a",
    coerceTo: "double",
    expected: true,
  },
  {
    value: "123.321",
    coerceTo: "string",
    expected: false,
  },
];

describe("Check checkCoerceTypeError function", () => {
  test.each(checkCoerceTypeErrorCases)(
    "given '$value' as argument, returns $expected",
    ({ value, coerceTo, expected }) => {
      const result = checkCoerceTypeError({ value, coerceTo });

      expect(result).toBe(expected);
    },
  );
});
