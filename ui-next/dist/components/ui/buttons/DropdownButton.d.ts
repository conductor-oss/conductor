import { MouseEvent, ReactNode } from "react";
import { MuiButtonProps } from "./MuiButton";
export type DropdownButtonProps = {
    buttonProps?: MuiButtonProps;
    children?: ReactNode;
    options: any[];
    isOpen?: boolean;
    onClick?: (e: MouseEvent<HTMLButtonElement>, open: boolean) => void;
    onClickAway?: (e: any) => void;
};
export default function DropdownButton({ children, options, buttonProps, isOpen, onClick, onClickAway, }: DropdownButtonProps): import("react").JSX.Element;
