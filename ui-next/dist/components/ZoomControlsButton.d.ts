import { MuiIconButtonProps } from "components/ui/buttons/MuiIconButton";
type ZoomControlsButtonProps = MuiIconButtonProps & {
    disabled?: boolean;
    tooltip?: string;
};
export declare const ZoomControlsButton: import("react").ForwardRefExoticComponent<Omit<ZoomControlsButtonProps, "ref"> & import("react").RefAttributes<HTMLButtonElement>>;
export {};
