import { SxProps, Theme } from "@mui/material";
import type { ConductorInputStyleProps } from "components/ui/inputs/ConductorInput";
export declare const labelScale: number;
export declare const baseLabelStyle: SxProps<Theme>;
export declare const inputLabelStyle: ({ theme, isFocused, error, isInputEmpty, }: ConductorInputStyleProps) => SxProps<Theme>;
export declare const formHelperStyle: ({ theme, isFocused, error, isInputEmpty, }: ConductorInputStyleProps) => SxProps<Theme>;
