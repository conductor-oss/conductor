import { PaletteMode, Theme } from "@mui/material";
import { Components } from "@mui/material/styles";

import { colors, fontSizes } from "../../tokens/variables";
import baseTheme from "../baseTheme";

export const SMALL_INPUT_HEIGHT = "36px";

export const inputLabelIdleStyles = {};

export const inputLabelFocusedStyles = {
  color: colors.black,
};

const formControls = (mode: PaletteMode): Components<Theme> => {
  const darkMode = mode === "dark";

  return {
    MuiFormControl: {
      defaultProps: {
        size: "small",
      },
      styleOverrides: {
        root: {
          display: "block",
        },
      },
    },
    MuiInputBase: {
      styleOverrides: {
        root: {
          fontSize: fontSizes.fontSize2,
        },
        input: {
          "&[type=number]::-webkit-inner-spin-button ": {
            appearance: "none",
            margin: 0,
          },
        },
        sizeSmall: {
          minHeight: SMALL_INPUT_HEIGHT,
        },
      },
    },
    MuiTextField: {
      defaultProps: {
        variant: "outlined",
        InputProps: {
          notched: false,
        },
        InputLabelProps: {
          shrink: true,
        },
      },
    },
    MuiCheckbox: {
      defaultProps: {
        size: "small",
      },
      styleOverrides: {
        root: ({ theme }) => ({
          fontSize: fontSizes.fontSize0,
          padding: theme.spacing(2),
        }),
        colorSecondary: ({ theme }) => ({
          color: colors.blackLight,
          "&$checked": {
            color: theme.palette.primary.main,
          },
          "&$disabled": {
            color: colors.blackXLight,
          },
        }),
      },
    },
    MuiSwitch: {
      styleOverrides: {
        root: {
          padding: 0,
          marginRight: 8,
          marginLeft: 8,
          height: 20,
          width: 40,
          "&:hover": {
            "& > $track": {
              backgroundColor: colors.gray05,
            },
            "& > $checked + $track": {
              backgroundColor: colors.brand05,
            },
          },
        },
        thumb: {
          borderRadius: 8,
          width: 16,
          height: 16,
          color: "white",
          boxShadow:
            "0px 1px 2px 0px rgba(0, 0, 0, 0.4), 0px 0px 1px 0px rgba(0, 0, 0, 0.4)",
        },
        track: ({ theme }) => ({
          backgroundColor: colors.gray07,
          borderRadius: 10,
          opacity: 1,
          ".Mui-checked.Mui-checked + &": {
            // track - checked
            backgroundColor: theme.palette.green.primary,
            opacity: 1,
          },
        }),
        switchBase: {
          padding: 2,
          "&$checked": {
            // transform: "translateX(100%)",
            "& + $track": {
              opacity: 1,
            },
          },
        },
        colorPrimary: ({ theme }) => ({
          "&$checked": {
            color: theme.palette.common.white,
          },
          "&$checked + $track": {
            backgroundColor: theme.palette.primary.main,
          },
        }),
      },
    },
    MuiRadio: {
      styleOverrides: {
        root: ({ theme }) => ({
          padding: theme.spacing(2),
        }),
      },
    },
    MuiOutlinedInput: {},
    MuiFormControlLabel: {
      styleOverrides: {
        root: {
          marginLeft: -8,
        },
      },
    },
    MuiInputLabel: {
      defaultProps: {
        shrink: true,
      },
      styleOverrides: {
        root: {
          pointerEvents: "auto",
          color: baseTheme.palette.text.primary,
          "&.MuiInputLabel-outlined": {
            "&.MuiInputLabel-focused": inputLabelFocusedStyles,
          },
        },
      },
    },
    MuiFormHelperText: {
      styleOverrides: {
        contained: ({ theme }) => ({
          margin: 0,
          marginTop: theme.spacing(2),
        }),
      },
    },
    MuiSelect: {
      styleOverrides: {
        icon: {
          fontSize: fontSizes.fontSize5,
          color:
            mode === "dark" ? colors.gray12 : baseTheme.palette.text.primary,
        },
      },
    },
    MuiAutocomplete: {
      defaultProps: {
        componentsProps: {
          paper: {
            elevation: 3,
          },
        },
      },
      styleOverrides: {
        paper: {
          fontSize: fontSizes.fontSize2,
          boxShadow: `0 0 10px ${
            darkMode ? colors.gray08 : "rgba(0, 0, 0, .3)"
          }`,
        },
        popupIndicator: {
          fontSize: fontSizes.fontSize5,
          color: baseTheme.palette.text.primary,
        },
        clearIndicator: {
          fontSize: fontSizes.fontSize5,
          color: darkMode ? colors.gray12 : baseTheme.palette.text.primary,
        },
        inputRoot: ({ theme }) => ({
          paddingLeft: theme.spacing(3),
          paddingRight: theme.spacing(3),
        }),
        tag: {
          "&:first-of-type": {
            marginLeft: 8,
          },
        },
        option: {
          "&.MuiAutocomplete-option.Mui-focused": {
            backgroundColor: darkMode ? colors.blue04 : colors.blue13,
          },
        },
      },
    },
  };
};

export default formControls;
