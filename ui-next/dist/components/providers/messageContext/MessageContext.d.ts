import { PopoverMessage } from "types/Messages";
type MessageState = {
    setMessage: (msg: PopoverMessage | null) => void;
};
export declare const MessageContext: import("react").Context<MessageState>;
export {};
