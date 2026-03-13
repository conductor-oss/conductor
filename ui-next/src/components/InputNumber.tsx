import { TextField, TextFieldProps } from "@mui/material";
import _isEmpty from "lodash/isEmpty";
import _isNil from "lodash/isNil";
import { ChangeEvent, FunctionComponent, KeyboardEvent } from "react";
import { disabledInputStyle } from "shared/styles";
import { logger } from "utils/logger";

type InputNumberProps = Omit<TextFieldProps, "onChange"> & {
  onChange: (val: number | null, event: ChangeEvent<HTMLInputElement>) => void;
};
const pattern = /^(0|[1-9]\d*)?$/;
const isaValidNumber = (value: string) => pattern.test(value);

function removeNonMatchingChars(str: string) {
  let result = "";
  for (let i = 0; i < str.length; i++) {
    if (pattern.test(str[i])) {
      result += str[i];
    }
  }
  return result;
}

/**
 * The requirement for this component was
 * "number" : null,
 *     "number" : 0,
 *    "number" : 10
 *  Meaning allow empty. and set to null if empty. no leading 0s
 * @param param0
 * @returns
 */
export const InputNumber: FunctionComponent<InputNumberProps> = ({
  onChange,
  value,
  ...restProps
}) => {
  const handleInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const incomingValue = event.target.value;
    const isValidNumber = isaValidNumber(incomingValue);
    if (onChange && isValidNumber) {
      onChange(_isEmpty(incomingValue) ? null : Number(incomingValue), event);
    } else if (!isValidNumber) {
      const result = removeNonMatchingChars(incomingValue);
      onChange(_isEmpty(result) ? null : Number(result), event);
    }

    if (restProps.type === "number") {
      logger.warn(
        "Setting type to number on InputNumber will not allow to add 0",
      );
    }
  };
  const handleOnKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (
      e.ctrlKey ||
      e.shiftKey ||
      e.key === "Backspace" ||
      e.key === "Enter" ||
      e.key === "Tab" ||
      e.key === "Delete" ||
      e.key === "ArrowLeft" ||
      e.key === "ArrowRight" ||
      e.key === "ArrowUp" ||
      e.key === "ArrowDown"
    )
      return;
    if (!isaValidNumber(e.key)) {
      e.preventDefault();
    }
  };
  return (
    <TextField
      {...restProps}
      value={_isNil(value) ? "" : value}
      onChange={handleInputChange}
      onKeyDown={handleOnKeyDown}
      sx={{ ...disabledInputStyle }}
    />
  );
};
