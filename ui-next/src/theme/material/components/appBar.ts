import { colors } from "../../tokens/variables";
import { PaletteMode, Theme } from "@mui/material";
import { Components } from "@mui/material/styles";

export const appBar = (mode: PaletteMode): Components<Theme> => {
  return {
    MuiAppBar: {
      styleOverrides: {
        root: {
          ...(mode === "light"
            ? {
                backgroundColor: colors.white,
                color: colors.primary,
                fontSize: "11pt !important",
                fontWeight: 400,
              }
            : {
                backgroundColor: colors.gray01,
                color: colors.primary,
                fontSize: "11pt !important",
                fontWeight: 400,
              }),
          boxShadow: "none !important",
          "& .MuiLink-underlineHover:hover": {
            textDecoration: "none !important",
          },
        },
      },
    },
  };
};

export default appBar;
