import { ChangeEvent } from "react";
import { TITLE_ALLOWED_CHARS } from "./constants/common";
/**
 * If chars are valid, call handler
 * @param handler
 * @param regExVal
 * @returns
 */
export const handleValidChars =
  (handler: (val: string) => void, regExVal: string = TITLE_ALLOWED_CHARS) =>
  (value: string) => {
    const regEx = new RegExp(regExVal);
    if (regEx.test(value)) {
      handler(value);
    }
  };

export const handleValidCharsForEvents =
  (
    handler: (evt: ChangeEvent<HTMLInputElement>) => void,
    regExVal: string = TITLE_ALLOWED_CHARS,
  ) =>
  (ov: ChangeEvent<HTMLInputElement>) => {
    const value = ov.target.value;
    const regEx = new RegExp(regExVal);
    if (regEx.test(value)) {
      handler({
        ...ov,
        target: { value },
      } as ChangeEvent<HTMLInputElement>);
    }
  };
