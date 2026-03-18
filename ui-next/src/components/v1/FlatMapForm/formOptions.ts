import _first from "lodash/first";

export const VARIABLE_REGEX = /\$\{[^}]*$/;

export const customFilterOptions = (options: string[], inputValue: string) => {
  const sanitizedInputValue = inputValue.toString().toLowerCase();
  if (sanitizedInputValue.endsWith("$")) {
    return options?.filter((option) =>
      option?.toString().toLowerCase().startsWith("$"),
    );
  } else if (VARIABLE_REGEX.test(sanitizedInputValue)) {
    const matchedValue = _first(sanitizedInputValue.match(VARIABLE_REGEX));
    if (matchedValue) {
      return options?.filter((option) =>
        option?.toString().toLowerCase().startsWith(matchedValue),
      );
    } else {
      return [];
    }
  } else {
    return options?.filter((option) =>
      option?.toString().toLowerCase().startsWith(sanitizedInputValue),
    );
  }
};
