import { ReactNode } from "react";
import { MuiButtonProps } from "components/ui/buttons/MuiButton";
import { MuiButtonGroupProps } from "components/ui/buttons/MuiButtonGroup";
type ConductorSplitButtonProps = MuiButtonGroupProps & MuiButtonProps & {
    options: {
        label: ReactNode;
        onClick: () => void;
        id?: string;
        disabled?: boolean;
    }[];
    primaryOnClick: () => void;
    children: ReactNode;
    tooltip?: string;
    "data-testid"?: string;
};
export default function SplitButton({ options, primaryOnClick, children, startIcon, tooltip, id, "data-testid": dataTestId, ...props }: ConductorSplitButtonProps): import("react").JSX.Element;
export type { ConductorSplitButtonProps };
