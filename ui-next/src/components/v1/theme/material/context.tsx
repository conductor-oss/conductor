import { PaletteMode } from "@mui/material";
import { createContext } from "react";

interface ThemeProviderContext {
  mode: PaletteMode;
  toggler?: {
    toggleColorMode: () => void;
  };
}

export const ColorModeContext = createContext<ThemeProviderContext>({
  mode: "light",
});
