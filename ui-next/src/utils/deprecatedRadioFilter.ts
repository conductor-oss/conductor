const options = [
  {
    value: "graaljs",
    label: "ECMASCRIPT",
  },
  {
    value: "javascript",
    label: "Javascript(deprecated)",
    disabled: true,
  },
  {
    value: "value-param",
    label: "Value-Param",
  },
  {
    // a native workflow's switch: its code decided, so it is shown, never chosen
    value: "native-code",
    label: "Native code",
    disabled: true,
  },
];

const shownOnlyWhenSet = new Set(["javascript", "native-code"]);

export const filterOptionByEvaluatorType = (evaluatorType?: string) => {
  return options.filter(
    (option) =>
      !shownOnlyWhenSet.has(option.value) || evaluatorType === option.value,
  );
};
