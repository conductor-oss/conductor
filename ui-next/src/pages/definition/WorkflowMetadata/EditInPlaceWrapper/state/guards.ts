import { EditInPlaceMachineContext, ChangeValueEvent } from "./types";

export const hasValidChars = (
  context: EditInPlaceMachineContext,
  { value }: ChangeValueEvent,
) => {
  if (context.allowedCharsRegEx) {
    const regEx = new RegExp(context.allowedCharsRegEx);
    return regEx.test(value);
  }
  return true;
};
