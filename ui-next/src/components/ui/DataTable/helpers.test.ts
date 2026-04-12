import { dynamicSort } from "./helpers";

const cases = [
  {
    name: "A greater than B",
    objA: {
      target: {
        type: "INTEGRATION_PROVIDER",
        id: "My Test integration",
      },
      access: ["UPDATE", "READ", "EXECUTE", "CREATE"],
      tag: "test:test",
    },
    objB: {
      target: {
        type: "APPLICATION",
        id: "app:8ee1b276-28a1-443c-b5a4-43892f87e222",
      },
      access: ["READ", "CREATE"],
      tag: "auto:test",
    },
    propertyPath: "target.type",
    expected: 1,
  },
  {
    name: "A lesser than B",
    objA: {
      target: {
        type: "INTEGRATION_PROVIDER",
        id: "0_My Test integration",
      },
      access: ["UPDATE", "READ", "EXECUTE", "CREATE"],
      tag: "test:test",
    },
    objB: {
      target: {
        type: "APPLICATION",
        id: "app:8ee1b276-28a1-443c-b5a4-43892f87e222",
      },
      access: ["READ", "CREATE"],
      tag: "auto:test",
    },
    propertyPath: "target.id",
    expected: -1,
  },
  {
    name: "A equal B",
    objA: {
      target: {
        type: "INTEGRATION_PROVIDER",
        id: "0_My Test integration",
        number: 9,
      },
      access: ["UPDATE", "READ", "EXECUTE", "CREATE"],
      tag: "test:test",
    },
    objB: {
      target: {
        type: "APPLICATION",
        id: "app:8ee1b276-28a1-443c-b5a4-43892f87e222",
        number: 9,
      },
      access: ["READ", "CREATE"],
      tag: "auto:test",
    },
    propertyPath: "target.number",
    expected: 0,
  },
  {
    name: "A and B have undefined value",
    objA: {
      target: {
        type: "INTEGRATION_PROVIDER",
        id: "0_My Test integration",
        number: 9,
      },
      access: ["UPDATE", "READ", "EXECUTE", "CREATE"],
      tag: "test:test",
    },
    objB: {
      target: {
        type: "APPLICATION",
        id: "app:8ee1b276-28a1-443c-b5a4-43892f87e222",
        number: 9,
      },
      access: ["READ", "CREATE"],
      tag: "auto:test",
    },
    propertyPath: "target.someProp.a.b.c",
    expected: 0,
  },
  {
    name: "A and B are arrays and have different length",
    objA: {
      target: {
        type: "INTEGRATION_PROVIDER",
        id: "0_My Test integration",
        number: 9,
      },
      access: ["UPDATE", "READ", "EXECUTE", "CREATE"],
      tag: "test:test",
    },
    objB: {
      target: {
        type: "APPLICATION",
        id: "app:8ee1b276-28a1-443c-b5a4-43892f87e222",
        number: 9,
      },
      access: ["READ", "CREATE"],
      tag: "auto:test",
    },
    propertyPath: "access",
    expected: 1,
  },
  {
    name: "A and B are different objects",
    objA: {
      target: {
        type: "INTEGRATION_PROVIDER",
        id: "0_My Test integration",
        number: 9,
      },
      access: ["UPDATE", "READ", "EXECUTE", "CREATE"],
      tag: "test:test",
    },
    objB: {
      target: {
        type: "APPLICATION",
        id: "app:8ee1b276-28a1-443c-b5a4-43892f87e222",
        number: 9,
      },
      access: ["READ", "CREATE"],
      tag: "auto:test",
    },
    propertyPath: "target",
    expected: 1,
  },
  {
    name: "A and B are equal objects",
    objA: {
      target: {
        type: "INTEGRATION_PROVIDER",
        id: "0_My Test integration",
        number: 9,
      },
      access: ["UPDATE", "READ", "EXECUTE", "CREATE"],
      tag: "test:test",
      equal: {
        a: 1,
        b: 2,
      },
    },
    objB: {
      target: {
        type: "APPLICATION",
        id: "app:8ee1b276-28a1-443c-b5a4-43892f87e222",
        number: 9,
      },
      access: ["READ", "CREATE"],
      tag: "auto:test",
      equal: {
        a: 1,
        b: 2,
      },
    },
    propertyPath: "equal",
    expected: 0,
  },
];

describe("Compare 2 objects for sorting", () => {
  test.each(cases)(
    "testing '$name', returns $expected",
    ({ objA, objB, propertyPath, expected }) => {
      const result = dynamicSort({ objA, objB, propertyPath });

      expect(result).toEqual(expected);
    },
  );
});
