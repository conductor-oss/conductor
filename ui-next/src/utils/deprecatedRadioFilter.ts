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
];

export const filterOptionByEvaluatorType = (evaluatorType?: string) => {
  return options.filter(
    (option) => option.value !== "javascript" || evaluatorType === "javascript",
  );
};
