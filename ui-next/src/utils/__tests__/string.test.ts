import { getSequentiallySuffix } from "utils/strings";

const cases = [
  {
    name: "test",
    refNames: ["test_1", "test_2", "test_12"],
    expected: {
      name: "test_3",
      taskReferenceName: "test_3",
    },
  },
  {
    name: "task-name",
    refNames: ["task-name_4", "task-name_5", "task-name_1"],
    expected: {
      name: "task-name_2",
      taskReferenceName: "task-name_2",
    },
  },
  {
    name: "task-name",
    refNames: [],
    expected: {
      name: "task-name",
      taskReferenceName: "task-name",
    },
  },
];

describe("Get sequential name", () => {
  test.each(cases)(
    "given '$name' and $refNames as arguments, returns $expected",
    ({ name, refNames, expected }) => {
      const result = getSequentiallySuffix({ name, refNames });

      expect(result).toMatchObject(expected);
    },
  );
});
