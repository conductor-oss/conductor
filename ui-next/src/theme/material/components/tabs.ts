import { colors, fontSizes } from "../../tokens/variables";
import { PaletteMode } from "@mui/material";

const tabs = (mode: PaletteMode) => {
  const darkMode = mode === "dark";

  return {
    MuiTabs: {
      styleOverrides: {
        indicator: {
          height: 2,
        },
        scroller: {
          backgroundColor: darkMode ? colors.black : colors.white,
        },
      },
    },
    MuiTab: {
      defaultProps: {
        disableRipple: true,
      },
      styleOverrides: {
        root: {
          textTransform: "none",
          color: darkMode ? colors.gray08 : undefined,
          "&.Mui-selected": {
            color: darkMode ? colors.gray14 : colors.gray00,
          },
          fontSize: fontSizes.fontSize2,
        },
      },
    },
  };
};

export default tabs;
