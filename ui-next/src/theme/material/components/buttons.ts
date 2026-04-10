import { PaletteMode, Theme } from "@mui/material";
import { Components } from "@mui/material/styles";
import { colors } from "theme/tokens/variables";

const lightButton: Partial<Components<Theme>> = {
  MuiButton: {
    defaultProps: {
      disableRipple: true,
      variant: "contained",
      color: "primary",
      size: "medium",
    },
    styleOverrides: {
      root: {
        textTransform: "none",
        borderRadius: "6px",
        transition: "none",
        fontWeight: 500,
        boxShadow: "none",
        padding: "8px 12px 8px 12px",
        border: "1px solid inherit",
      },
      sizeSmall: {
        minHeight: "28px",
        height: "28px",
        fontSize: "12px",
      },
      sizeMedium: {
        minHeight: "36px",
        height: "36px",
        fontSize: "14px",
        fontWeight: 500,
      },
      sizeLarge: {
        minHeight: "50px",
        height: "50px",
        fontSize: "16px",
      },
      contained: {
        border: `1px solid ${colors.sidebarFaintGrey}`,
      },
      containedPrimary: {
        color: colors.white,
        backgroundColor: colors.blueLightMode,
        border: `1px solid ${colors.blueLightMode}`,

        ":hover": {
          color: colors.white,
          backgroundColor: colors.blueLightMode,
          border: `1px solid ${colors.blueLightMode}`,
          boxShadow: `3px 3px 0px 0px ${colors.primaryHoverBoxShadow}`,
        },

        ":active": {
          color: colors.sidebarBlacky,
          backgroundColor: colors.darkBlueLightMode,
          borderColor: colors.darkBlueLightMode,
          boxShadow: "none",
        },

        "&.Mui-disabled": {
          color: colors.defaultModalBackdropColor,
          backgroundColor: colors.sidebarBarelyPastWhite,
          borderColor: colors.defaultModalBackdropColor,
        },
      },
      outlinedPrimary: {
        color: colors.sidebarBlacky,
        backgroundColor: undefined,
        border: `1px solid ${colors.blueLight}`,
      },
      textPrimary: {
        color: colors.blueLightMode,
        ":hover": {
          backgroundColor: "unset",
        },
      },
      containedSecondary: {
        color: colors.blueLightMode,
        backgroundColor: colors.white,
        border: `1px solid ${colors.blueLightMode}`,

        ":hover": {
          color: colors.blueLightMode,
          backgroundColor: colors.white,
          border: `1px solid ${colors.blueLightMode}`,
          boxShadow: `3px 3px 0px 0px ${colors.secondaryHoverBoxShadow}`,
        },

        ":active": {
          color: colors.sidebarBlacky,
          backgroundColor: colors.darkBlueLightMode,
          borderColor: colors.darkBlueLightMode,
          boxShadow: "none",
        },

        "&.Mui-disabled": {
          color: colors.defaultModalBackdropColor,
          backgroundColor: colors.sidebarBarelyPastWhite,
          borderColor: colors.defaultModalBackdropColor,
        },
      },
      outlinedSecondary: {
        color: colors.sidebarGreyDark,
        borderColor: colors.sidebarGreyDark,
      },
      textSecondary: {
        color: colors.sidebarGreyDark,
        ":hover": {
          backgroundColor: "unset",
        },
      },
      // @ts-ignore
      containedTertiary: {
        color: colors.sidebarGrey,
        backgroundColor: colors.white,
        border: `1px solid ${colors.sidebarFaintGrey}`,

        ":hover": {
          color: colors.greyBg,
          backgroundColor: colors.white,
          borderColor: colors.sidebarFaintGrey,
          boxShadow: `3px 3px 0px 0px ${colors.tertiaryHoverBoxShadow}`,
        },

        ":active": {
          color: colors.sidebarGreyDark,
          backgroundColor: colors.darkBlueLightMode,
          borderColor: colors.darkBlueLightMode,
          boxShadow: "none",
        },

        "&.Mui-disabled": {
          color: colors.defaultModalBackdropColor,
          backgroundColor: colors.sidebarBarelyPastWhite,
          borderColor: colors.defaultModalBackdropColor,
        },
      },
      outlinedTertiary: {
        color: colors.sidebarGrey,
        border: `1px solid ${colors.sidebarGrey}`,

        ":hover": {
          color: colors.greyBg,
          borderColor: colors.sidebarFaintGrey,
        },
      },
      textTertiary: {
        color: colors.sidebarGrey,
      },
      containedError: {
        border: `1px solid ${colors.red07}`,

        ":hover": {
          backgroundColor: colors.red07,
          border: `1px solid ${colors.red07}`,
          boxShadow: `3px 3px 0px 0px ${colors.primaryHoverBoxShadow}`,
        },

        ":active": {
          color: colors.sidebarBlacky,
          backgroundColor: colors.red07,
          borderColor: colors.red07,
          boxShadow: "none",
        },

        "&.Mui-disabled": {
          color: colors.defaultModalBackdropColor,
          backgroundColor: colors.sidebarBarelyPastWhite,
          borderColor: colors.defaultModalBackdropColor,
        },
      },
    },
  },
  MuiIconButton: {
    styleOverrides: {
      root: {
        color: colors.gray04,
        borderColor: colors.gray04,
        "&.Mui-disabled": {
          color: colors.gray08,
          borderColor: colors.gray08,
        },
        "&:hover": {
          backgroundColor: undefined,
        },
      },
    },
  },
};

const darkButton: Partial<Components<Theme>> = {
  MuiButton: {
    defaultProps: {
      disableRipple: true,
      variant: "contained",
      color: "primary",
      size: "medium",
    },
    styleOverrides: {
      root: {
        textTransform: "none",
        borderRadius: "6px",
        transition: "none",
        fontWeight: 500,
        boxShadow: "none",
        padding: "8px",

        "&.Mui-disabled": {
          border: "none",
          color: colors.sidebarFaintGrey,
          backgroundColor: colors.sidebarBarelyPastWhite,
        },

        ":after": {
          content: '""',
          position: "absolute",
          zIndex: -1,
          right: 0,
          bottom: 0,
          width: "100%",
          height: "100%",
          background: `${colors.blueBackground}`,
          border: `1px solid ${colors.sidebarGreyDark}`,
          borderRadius: "6px",
          opacity: 0,
          transition: "opacity 0.3s ease-in-out, transform 0.3s ease-in-out",
        },

        ":hover": {
          color: colors.sidebarFaintGrey,
          backgroundColor: colors.sidebarGreyDark,
          border: `1px solid ${colors.sidebarGreyDark}`,

          ":after": {
            opacity: 1,
            right: -5,
            bottom: -5,
          },
        },
      },
      sizeSmall: {
        minHeight: "28px",
        height: "28px",
        fontSize: "10pt",
      },
      sizeMedium: {
        minHeight: "36px",
        height: "36px",
        fontSize: "11pt",
        fontWeight: 500,
      },
      sizeLarge: {
        minHeight: "50px",
        height: "50px",
        fontSize: "14pt",
      },
      outlinedPrimary: {},
      outlinedSecondary: {
        color: colors.gray12,
        borderColor: colors.gray12,
        "&.Mui-disabled": {
          color: colors.gray05,
          borderColor: colors.gray05,
        },
      },
      contained: {
        border: `1px solid ${colors.sidebarFaintGrey}`,
      },
      containedPrimary: {
        "&.Mui-disabled": {
          color: colors.gray09,
          backgroundColor: colors.gray05,
        },
      },
      containedSecondary: {
        "&.Mui-disabled": {
          color: colors.gray05,
          backgroundColor: colors.gray02,
        },
      },
    },
  },
  MuiIconButton: {
    styleOverrides: {
      root: {
        color: colors.gray12,
        borderColor: colors.gray12,
        "&.Mui-disabled": {
          color: colors.gray05,
          borderColor: colors.gray05,
        },
        "&:hover": {
          backgroundColor: colors.gray06,
        },
      },
    },
  },
};

const buttons = (mode: PaletteMode): Components<Theme> => {
  return mode === "dark" ? darkButton : lightButton;
};

export default buttons;
