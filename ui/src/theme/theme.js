import { unstable_createMuiStrictModeTheme as createMuiTheme } from "@material-ui/core/styles";
import {
  borders,
  colors,
  spacings,
  breakpoints,
  fontSizes,
  lineHeights,
  fontWeights,
  fontFamily,
} from "./variables";

function toNumber(v) {
  return parseFloat(v);
}

const spacingFn = (factor) => {
  const unit = toNumber(spacings.space0);

  // Support theme.spacing('space3')
  if (typeof factor === "string") {
    return toNumber(spacings[factor]);
  }

  if (typeof factor === "number") {
    // Support theme.spacing(2)
    return unit * factor;
  }

  return unit;
};

const colorFn = (color) => colors[color];

const baseThemeOptions = {
  mixins: {
    toolbar: {
      minHeight: 80,
    },
  },
  palette: {
    type: "light",
    primary: {
      main: colors.brand,
      light: colors.bgBrandLight,
      dark: colors.bgBrandDark,
      contrastText: colors.white,
    },
    secondary: {
      main: colors.white,
      light: colors.bgBrandLight,
      dark: colors.bgBrandDark,
      contrastText: colors.black,
    },
    text: {
      primary: colors.black,
      secondary: colors.blackXLight,
      disabled: colors.blackXXLight,
      hint: colors.blackXXLight,
    },
    grey: {
      50: colors.gray14,
      100: colors.gray13,
      200: colors.gray12,
      300: colors.gray11,
      400: colors.gray10,
      500: colors.gray09,
      600: colors.gray07,
      700: colors.gray06,
      800: colors.gray04,
      900: colors.gray02,
      A100: colors.gray12,
      A200: colors.gray08,
      A400: colors.gray03,
      A700: colors.gray06,
    },
    error: {
      main: colors.failure,
      light: colors.failureLight,
      dark: colors.failureDark,
      contrastText: colors.white,
    },
    background: {
      paper: colors.white,
      default: colors.gray14,
    },
    divider: colors.blackXXLight,
  },
  typography: {
    fontFamily: fontFamily.fontFamilySans,
    fontSize: toNumber(fontSizes.fontSize2),
    htmlFontSize: toNumber(fontSizes.fontSize2),
    fontWeightLight: fontWeights.fontWeight0,
    fontWeightRegular: fontWeights.fontWeight0,
    fontWeightMedium: fontWeights.fontWeight1,
    fontWeightBold: fontWeights.fontWeight2,
    h1: {
      fontSize: fontSizes.fontSize10,
      lineHeight: lineHeights.lineHeight0,
      fontWeight: fontWeights.fontWeight2,
    },
    h2: {
      fontSize: fontSizes.fontSize9,
      lineHeight: lineHeights.lineHeight0,
      fontWeight: fontWeights.fontWeight2,
    },
    h3: {
      fontSize: fontSizes.fontSize8,
      lineHeight: lineHeights.lineHeight0,
      fontWeight: fontWeights.fontWeight2,
    },
    h4: {
      fontSize: fontSizes.fontSize7,
      lineHeight: lineHeights.lineHeight0,
      fontWeight: fontWeights.fontWeight2,
    },
    h5: {
      fontSize: fontSizes.fontSize6,
      lineHeight: lineHeights.lineHeight0,
      fontWeight: fontWeights.fontWeight2,
    },
    h6: {
      fontSize: fontSizes.fontSize5,
      lineHeight: lineHeights.lineHeight0,
      fontWeight: fontWeights.fontWeight2,
    },
    body1: {
      fontSize: fontSizes.fontSize4,
      lineHeight: lineHeights.lineHeight1,
    },
    body2: {
      fontSize: fontSizes.fontSize3,
      lineHeight: lineHeights.lineHeight1,
    },
    caption: {
      fontSize: fontSizes.fontSize2,
      lineHeight: lineHeights.lineHeight1,
    },
    button: {
      fontSize: fontSizes.fontSize2,
      fontWeight: fontWeights.fontWeight1,
    },
  },
  breakpoints: {
    // this looks wrong, but it's not
    // material's breakpoints are a range, so the below basically says
    // xs is from 0 to breakpoints.large
    values: {
      xs: 0,
      sm: toNumber(breakpoints.xsmall),
      md: toNumber(breakpoints.small),
      lg: toNumber(breakpoints.medium),
      xl: toNumber(breakpoints.large),
    },
  },
  shape: {
    borderRadius: toNumber(borders.radiusSmall),
  },
  color: colorFn,
  spacing: spacingFn,
  props: {
    MuiButtonBase: {
      disableRipple: true,
    },
    MuiFormControl: {
      variant: "outlined",
    },
    MuiMenu: {
      transitionDuration: 0,
      elevation: 3,
    },
    MuiTextField: {
      variant: "outlined",
      InputProps: {
        labelWidth: 0,
        notched: false,
      },
      InputLabelProps: {
        shrink: true,
      },
    },
    MuiInputLabel: {
      shrink: true,
    },
    MuiOutlinedInput: {
      notched: false,
    },
    MuiPaper: {
      elevation: 3,
    },
    MuiPopover: {
      elevation: 3,
    },
  },
};

const baseTheme = createMuiTheme(baseThemeOptions);

// Keep overrides in separate object so we can reference attributes of baseTheme.
const overrides = {
  overrides: {
    MuiSvgIcon: {
      root: {
        fontSize: fontSizes.fontSize6,
      },
    },
    MuiAvatar: {
      root: {
        fontSize: "2.4rem",
      },
    },
    MuiButton: {
      root: {
        textTransform: "none",
        paddingTop: baseTheme.spacing("space1"),
        paddingBottom: baseTheme.spacing("space1"),
        paddingLeft: baseTheme.spacing("space2"),
        paddingRight: baseTheme.spacing("space2"),
        border: "1px solid transparent",
        transition: "none",
        "&$focusVisible": {
          boxShadow: "none",
          position: "relative",
          "&:after": {
            content: '""',
            display: "block",
            position: "absolute",
            width: "calc(100% + 6px)",
            height: "calc(100% + 6px)",
            borderRadius: borders.radiusSmall,
            border: borders.blueRegular2px,
            top: -5,
            left: -5,
          },
        },
      },
      text: {
        paddingTop: baseTheme.spacing("space1"),
        paddingBottom: baseTheme.spacing("space1"),
        paddingLeft: baseTheme.spacing("space2"),
        paddingRight: baseTheme.spacing("space2"),
        "&:hover": {
          backgroundColor: baseTheme.palette.grey.A100,
        },
      },
      textSizeSmall: {
        fontSize: "0.8125rem",
      },
      outlined: {
        paddingTop: baseTheme.spacing("space1"),
        paddingBottom: baseTheme.spacing("space1"),
        paddingLeft: baseTheme.spacing("space2"),
        paddingRight: baseTheme.spacing("space2"),
      },
      outlinedPrimary: {
        border: borders.blackRegular1px,
      },
      outlinedSecondary: {
        border: borders.blackLight1px,
        color: baseTheme.palette.secondary.contrastText,
        "&:hover": {
          border: borders.blackLight1px + " !important",
          backgroundColor: baseTheme.palette.grey.A100,
        },
      },
      contained: {
        "&:disabled": {
          backgroundColor: colors.bgBrandLight,
          color: baseTheme.palette.common.white,
        },
        boxShadow: "none !important",
        "&:active": {
          boxShadow: "none !important",
        },
      },
    },
    MuiCheckbox: {
      root: {
        fontSize: fontSizes.fontSize4,
        padding: baseTheme.spacing("space1"),
      },
      colorSecondary: {
        color: colors.blackLight,
        "&$checked": {
          color: baseTheme.palette.primary.main,
        },
        "&$disabled": {
          color: colors.blackXLight,
        },
      },
    },
    MuiChip: {
      root: {
        borderRadius: borders.radiusSmall,
        height: 24,
        fontSize: fontSizes.fontSize2,
        fontWeight: fontWeights.fontWeight1,
      },
      label: {
        paddingLeft: baseTheme.spacing("space1"),
        paddingRight: baseTheme.spacing("space1"),
      },
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
    MuiRadio: {
      root: {
        padding: baseTheme.spacing("space1"),
      },
    },
    MuiInputBase: {
      root: {
        fontSize: fontSizes.fontSize2,
      },
      input: {
        "&[type=number]::-webkit-inner-spin-button ": {
          appearance: "none",
          margin: 0,
        },
      },
    },
    MuiOutlinedInput: {
      notchedOutline: {
        borderColor: colors.blackXXLight,
        top: 0,
        "& legend": {
          // force-disable notched legends
          display: "none",
        },
      },
      root: {
        "&:hover $notchedOutline": {
          borderColor: colors.blackXXLight,
        },
        backgroundColor: baseTheme.palette.background.paper,
      },
      input: {
        padding: `${baseTheme.spacing("space2")}px ${baseTheme.spacing(
          "space2"
        )}px`,
      },
    },
    MuiFormControl: {
      root: {
        display: "block",
      },
    },
    MuiFormControlLabel: {
      root: {
        marginLeft: -8,
      },
    },
    MuiInputLabel: {
      root: {
        pointerEvents: "none",
        color: baseTheme.palette.text.primary,
      },
      outlined: {
        "&$shrink": {
          transform: "none",
          position: "relative",
          fontWeight: fontWeights.fontWeight1,
          fontSize: fontSizes.fontSize2,
          paddingLeft: 0,
          paddingBottom: 8,
        },
        "&$focused": {
          // focused attr under MuiInputLabel does not work
          color: baseTheme.palette.text.primary,
        },
      },
    },
    MuiFormHelperText: {
      contained: {
        margin: 0,
        marginTop: baseTheme.spacing("space1"),
      },
    },
    MuiSelect: {
      icon: {
        fontSize: fontSizes.fontSize5,
        marginTop: 3,
        color: baseTheme.palette.text.primary,
      },
      selectMenu: {},
    },
    MuiPickersClockNumber: {
      clockNumber: {
        top: 6,
      },
    },
    MuiMenuItem: {
      root: {
        color: baseTheme.palette.text.primary,
        fontSize: fontSizes.fontSize1,
        "&:hover": {
          backgroundColor: baseTheme.palette.grey[100],
        },
        "&:focus": {
          backgroundColor: baseTheme.palette.grey[100],
        },
        "&$selected": {
          backgroundColor: baseTheme.palette.grey[200],
          "&:hover": {
            backgroundColor: baseTheme.palette.grey[200],
          },
          "&:focus": {
            backgroundColor: baseTheme.palette.grey[200],
          },
        },
      },
      dense: {
        paddingTop: 0,
        paddingBottom: 0,
      },
    },
    MuiSnackbarContent: {
      root: {
        backgroundColor: baseTheme.palette.primary.main,
        paddingTop: 0,
        paddingBottom: 0,
        marginRight: baseTheme.spacing("space3"),
        marginLeft: baseTheme.spacing("space3"),
        borderRadius: baseTheme.shape.borderRadius,
        boxShadow: "none",
      },
      action: {
        "& button": {
          color: baseTheme.palette.common.white,
        },
      },
    },
    MuiSwitch: {
      root: {
        padding: 0,
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
        boxShadow:
          "0px 1px 2px 0px rgba(0, 0, 0, 0.4), 0px 0px 1px 0px rgba(0, 0, 0, 0.4)",
      },
      track: {
        backgroundColor: colors.gray07,
        borderRadius: 10,
        opacity: 1,
      },
      switchBase: {
        padding: 2,
        "&$checked": {
          transform: "translateX(100%)",
          "& + $track": {
            opacity: 1,
          },
        },
      },
      colorPrimary: {
        "&$checked": {
          color: baseTheme.palette.common.white,
        },
        "&$checked + $track": {
          backgroundColor: baseTheme.palette.primary.main,
        },
      },
    },
    MuiTab: {
      root: {
        textTransform: "none",
        "&$selected": {
          color: "black",
        },
      },
    },
    MuiTabs: {
      indicator: {
        height: 4,
      },
    },
    MuiListItemText: {
      secondary: {
        fontSize: fontSizes.fontSize2,
      },
      primary: {
        fontSize: fontSizes.fontSize2,
      },
    },
    MuiListSubheader: {
      root: {
        fontSize: fontSizes.fontSize2,
        lineHeight: lineHeights.lineHeight1,
        paddingTop: baseTheme.spacing("space0"),
        paddingBottom: baseTheme.spacing("space0"),
      },
    },
    MuiTableCell: {
      root: {
        fontSize: fontSizes.fontSize2,
      },
      head: {
        //border: 'none',
        fontWeight: fontWeights.fontWeight1,
        color: colors.gray05,
      },
    },
    MuiTableRow: {
      root: {
        "&.Mui-selected:hover": {
          backgroundColor: colors.gray12,
        },
        "&.Mui-selected": {
          backgroundColor: `${colors.gray12} !important`,
        },
      },
    },
    MuiDialogTitle: {
      root: {
        backgroundColor: baseTheme.palette.grey[50],
        padding: `${baseTheme.spacing("space5")}px ${baseTheme.spacing(
          "space4"
        )}px`,
        borderBottom: `1px solid ${colors.blackXXLight}`,
      },
    },
    MuiDialogContent: {
      root: {
        padding: baseTheme.spacing("space5"),
      },
    },
    MuiDialogActions: {
      root: {
        backgroundColor: baseTheme.palette.grey[50],
        padding: `${baseTheme.spacing("space3")}px ${baseTheme.spacing(
          "space5"
        )}px`,
        borderTop: `1px solid ${colors.blackXXLight}`,
        margin: 0,

        "button + button": {
          marginLeft: baseTheme.spacing("space1"),
        },
      },
    },
    MuiAppBar: {
      colorPrimary: {
        backgroundColor: colors.white,
        color: colors.gray00,
      },
      root: {
        zIndex: 0,
        paddingLeft: 20,
        paddingRight: 20,
        boxShadow: "0 4px 8px 0 rgb(0 0 0 / 10%), 0 0 2px 0 rgb(0 0 0 / 10%)",

        "& .MuiButton-label": {
          color: colors.black,
        },
        "& .MuiLink-underlineHover:hover": {
          textDecoration: "none !important",
        },
      },
    },
    MuiAutocomplete: {
      input: {
        padding: "12px 16px !important",
      },
      paper: {
        fontSize: fontSizes.fontSize2,
      },
      popupIndicator: {
        fontSize: fontSizes.fontSize5,
        color: baseTheme.palette.text.primary,
      },
      clearIndicator: {
        fontSize: fontSizes.fontSize5,
        color: baseTheme.palette.text.primary,
      },
      inputRoot: {
        padding: "0px !important",
      },
      listbox: {
        backgroundColor: baseTheme.palette.common.white,
      },
      tag: {
        "&:first-child": {
          marginLeft: 8,
        },
      },
    },
    MuiTablePagination: {
      select: {
        paddingRight: "32px !important",
      },
      selectRoot: {
        top: 1,
      },
    },
  },
};

const finalTheme = createMuiTheme({
  ...baseTheme,
  ...overrides,
});

export default finalTheme;
