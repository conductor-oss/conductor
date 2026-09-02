import { PaletteMode } from "@mui/material";
interface ThemeProviderContext {
    mode: PaletteMode;
    toggler?: {
        toggleColorMode: () => void;
    };
}
export declare const ColorModeContext: import("react").Context<ThemeProviderContext>;
export {};
