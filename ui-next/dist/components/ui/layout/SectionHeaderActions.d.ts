import { MuiButtonProps } from "components/ui/buttons/MuiButton";
import { ReactNode } from "react";
interface IActionButton extends MuiButtonProps {
    label?: string;
    customButtonElement?: ReactNode;
}
declare const SectionHeaderActions: ({ buttons }: {
    buttons: IActionButton[];
}) => import("react").JSX.Element;
export default SectionHeaderActions;
