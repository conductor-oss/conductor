import { SnackbarMessage } from "components/SnackbarMessage";
import { ReactNode, useState } from "react";
import { PopoverMessage } from "types/Messages";
import { MessageContext } from "./MessageContext";

const defaultMessage = null;

interface MessageProviderProps {
  children?: ReactNode;
}

export const MessageProvider = ({ children }: MessageProviderProps) => {
  const [message, setMessage] = useState<PopoverMessage | null>(defaultMessage);

  return (
    <>
      {message ? (
        <SnackbarMessage
          id="global-snackbar-message"
          message={message.text}
          severity={message.severity}
          onDismiss={() => setMessage(defaultMessage)}
        />
      ) : null}

      <MessageContext.Provider value={{ setMessage }}>
        {children}
      </MessageContext.Provider>
    </>
  );
};
