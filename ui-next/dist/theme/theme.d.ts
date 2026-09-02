import type { ConductorInputStyleProps } from "components/ui/inputs/ConductorInput";
import { PaletteMode } from "@mui/material";
import { ThemeOptions } from "@mui/material/styles";
declare module "@mui/material/Button" {
    interface ButtonPropsColorOverrides {
        tertiary: true;
    }
}
declare module "@mui/material/ButtonGroup" {
    interface ButtonGroupPropsColorOverrides {
        tertiary: true;
    }
}
export declare const getOverridesForMode: (mode: PaletteMode) => ThemeOptions;
export declare const getTheme: (mode?: PaletteMode) => import("@mui/material").Theme;
export default getTheme;
export declare const LOCAL_STORAGE_DARK_MODE_TOGGLE_KEY = "dark-mode-toggle";
export declare const getColor: ({ theme, isFocused, error, isLabel, isInputEmpty, }: ConductorInputStyleProps) => string;
