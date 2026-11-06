import { asciiSafeJson, getSequentiallySuffix } from "utils/strings";

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

describe("asciiSafeJson", () => {
  test("leaves ASCII-only strings untouched", () => {
    const input = '{"name":"hello","version":1}';
    expect(asciiSafeJson(input)).toBe(input);
  });

  test("escapes em-dash as \\u2014", () => {
    const result = asciiSafeJson('{"prompt":"return 0 rows — if so"}');
    expect(result).toBe('{"prompt":"return 0 rows \\u2014 if so"}');
    expect(result).not.toContain("—");
  });

  test("result is pure ASCII (survives WAF C1-byte stripping unchanged)", () => {
    const input = '{"a":"—","b":"é","c":"中"}';
    const result = asciiSafeJson(input);
    expect([...result].every((c) => c.charCodeAt(0) <= 127)).toBe(true);
  });

  test("round-trips through JSON.parse correctly", () => {
    const original = { prompt: "return 0 rows — if so", name: "café" };
    const safe = asciiSafeJson(JSON.stringify(original));
    expect(JSON.parse(safe)).toEqual(original);
  });

  test("does not escape characters below U+0080", () => {
    const input = '{"key":"value with spaces and punctuation: !@#$%"}';
    expect(asciiSafeJson(input)).toBe(input);
  });
});

describe("Get sequential name", () => {
  test.each(cases)(
    "given '$name' and $refNames as arguments, returns $expected",
    ({ name, refNames, expected }) => {
      const result = getSequentiallySuffix({ name, refNames });

      expect(result).toMatchObject(expected);
    },
  );
});
