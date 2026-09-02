import { MuiButtonProps } from "./MuiButton";
export interface IActionButtonProps extends MuiButtonProps {
    progress?: boolean;
}
declare const ActionButton: ({ children, disabled, onClick, progress, ...props }: IActionButtonProps) => import("react").JSX.Element;
export default ActionButton;
