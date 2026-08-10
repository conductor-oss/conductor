import { PaletteMode, Theme } from "@mui/material";
import { Components } from "@mui/material/styles";
import {
  borders,
  colors,
  fontSizes,
  fontWeights,
} from "../../tokens/variables";
import baseTheme from "../baseTheme";

const atoms = (_mode: PaletteMode): Components<Theme> => ({
  MuiAvatar: {
    styleOverrides: {
      root: {
        fontSize: "2.4rem",
      },
    },
  },
  MuiLink: {
    styleOverrides: {
      root: {
        textDecoration: "none",
        color: colors.primary,
        //color: mode === "light" ? colors.primary : colors.primaryLighter,
      },
    },
  },
  MuiSvgIcon: {
    styleOverrides: {
      root: {
        fontSize: fontSizes.fontSize6,
      },
    },
  },
  MuiChip: {
    styleOverrides: {
      root: {
        borderRadius: borders.radiusSmall,
        height: "24px",
        fontSize: fontSizes.fontSize2,
        fontWeight: fontWeights.fontWeight1,
      },
      label: ({ theme }) => ({
        paddingLeft: theme.spacing(2),
        paddingRight: theme.spacing(2),
        lineHeight: "27px",
      }),
      deleteIcon: {
        height: "100%",
        padding: 3,
        margin: 0,
        backgroundColor: "rgba(5, 5, 5, 0.1)",
        borderRadius: `0 ${borders.radiusSmall} ${borders.radiusSmall} 0`,
        width: 24,
        boxSizing: "border-box",
        textAlign: "center",
        fill: baseTheme.palette.common.white,
        borderLeftWidth: 1,
        borderLeftStyle: "solid",
        borderLeftColor: "rgba(5, 5, 5, 0.1)",
      },
      deleteIconColorPrimary: {
        color: colors.white,
      },
      colorSecondary: {
        color: colors.white,
        backgroundColor: colors.lime07,
      },
    },
  },
});

export default atoms;
