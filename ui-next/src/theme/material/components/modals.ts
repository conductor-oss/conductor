import { colors } from "../../tokens/variables";
import { PaletteMode, Theme } from "@mui/material";
import { Components } from "@mui/material/styles";

const modals = (mode: PaletteMode): Components<Theme> => {
  return {
    MuiDialog: {
      styleOverrides: {
        paper: ({ theme }) => ({
          borderRadius: theme.spacing(3),
          boxShadow: theme.shadows[mode === "dark" ? 24 : 16],
        }),
      },
    },
    MuiDialogContent: {
      styleOverrides: {
        root: ({ theme }) => ({
          padding: theme.spacing(6),
        }),
      },
    },
    MuiDialogActions: {
      styleOverrides: {
        root: ({ theme }) => ({
          padding: `${theme.spacing(4)} ${theme.spacing(6)}`,
          background: colors.blackXXLight,
          borderTop: `1px solid ${colors.blackXXLight}`,
          margin: 0,
          gap: `${theme.spacing(4)}`,
        }),
      },
    },
    MuiDialogTitle: {
      styleOverrides: {
        root: ({ theme }) => ({
          fontSize: theme.typography.pxToRem(14),
          padding: `${theme.spacing(6)}px ${theme.spacing(5)}px`,
          background: colors.blackXXLight,
          borderBottom: `1px solid ${colors.blackXXLight}`,
        }),
      },
    },
  };
};

export default modals;
