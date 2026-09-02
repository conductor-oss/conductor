import { FunctionComponent, ReactNode } from "react";
import { StackProps } from "@mui/material";
import { MuiButtonProps } from "components/ui/buttons/MuiButton";
export interface ActionButton extends MuiButtonProps {
    label?: ReactNode;
    hidden?: boolean;
}
export interface ConductorSectionHeaderProps extends Omit<StackProps, "title"> {
    title: ReactNode;
    id?: string;
    versionSelector?: {
        current: number;
        available: number[];
        onChange: (version: number) => void;
    };
    buttons?: ActionButton[];
    buttonsComponent?: ReactNode;
    breadcrumbItems?: {
        label: string;
        to: string;
        icon?: ReactNode;
    }[];
}
export declare const ConductorSectionHeader: FunctionComponent<ConductorSectionHeaderProps>;
