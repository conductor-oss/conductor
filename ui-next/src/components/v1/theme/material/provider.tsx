import { PaletteMode } from "@mui/material";
import { ThemeProvider } from "@mui/material/styles";
import { FunctionComponent, ReactNode, useMemo, useState } from "react";
import { getTheme } from "../theme";
import { ColorModeContext } from "./context";

export const ColorModeProvider: FunctionComponent<{
  children: ReactNode;
}> = ({ children, ...rest }) => {
  const [mode, setMode] = useState<PaletteMode>("light");
  const toggler = useMemo(
    () => ({
      toggleColorMode: () => {
        setMode((prevMode) => (prevMode === "light" ? "dark" : "light"));
      },
    }),
    [],
  );

  // Update the theme only if the mode changes
  const lightOrDarkTheme = useMemo(() => {
    return getTheme(mode);
  }, [mode]);

  return (
    <ColorModeContext.Provider value={{ toggler, mode }}>
      <ThemeProvider theme={lightOrDarkTheme} {...rest}>
        {children}
      </ThemeProvider>
    </ColorModeContext.Provider>
  );
};
