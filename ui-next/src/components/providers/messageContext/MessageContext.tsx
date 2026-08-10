import { createContext } from "react";
import { PopoverMessage } from "types/Messages";

type MessageState = {
  setMessage: (msg: PopoverMessage | null) => void;
};

export const MessageContext = createContext<MessageState>({
  setMessage: () => null,
});
