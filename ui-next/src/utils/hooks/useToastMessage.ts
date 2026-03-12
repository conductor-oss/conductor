import { MessageContext } from "components/v1/layout/MessageContext";
import { useCallback, useContext } from "react";
import { PopoverMessage } from "types/Messages";

export const useToastMessage = () => {
  const { setMessage } = useContext(MessageContext);

  const toastMessage = useCallback(
    ({ text, severity }: PopoverMessage) => {
      setMessage({
        text,
        severity,
      });
    },
    [setMessage],
  );

  return {
    toastMessage,
  };
};
