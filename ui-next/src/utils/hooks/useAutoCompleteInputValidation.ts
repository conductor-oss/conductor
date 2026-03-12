import { useState } from "react";

export const useAutoCompleteInputValidation = (initialValue = "") => {
  const [value, setValue] = useState(initialValue);
  const [isFocused, setFocused] = useState(false);
  const hasError = !!value && !isFocused;

  return {
    value,
    setValue,
    isFocused,
    setFocused,
    hasError,
  };
};
