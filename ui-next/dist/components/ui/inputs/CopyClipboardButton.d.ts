import { IconButtonProps } from "@mui/material/IconButton";
export type CopyClipboardButtonProps = IconButtonProps & {
    text: string;
    message?: string;
};
export declare const CopyClipboardButton: ({ text, message, onClick, }: CopyClipboardButtonProps) => import("react").JSX.Element;
