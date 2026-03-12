import { PaletteMode, Theme } from "@mui/material";
import { Components } from "@mui/material/styles";
import { colors } from "theme/tokens/variables";

const lightButtonGroup: Partial<Components<Theme>> = {
  MuiButtonGroup: {
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
        "&.Mui-disabled": {
          color: colors.defaultModalBackdropColor,
          backgroundColor: colors.sidebarBarelyPastWhite,
          borderColor: colors.defaultModalBackdropColor,
        },
      },

      groupedContainedPrimary: {
        color: colors.white,
        backgroundColor: colors.blueLightMode,
        border: `1px solid ${colors.blueLightMode}`,

        ":not(:last-of-type)": {
          border: `none`,
          borderRight: "1px solid white",
        },

        ":hover": {
          color: colors.white,
          backgroundColor: colors.blueLightMode,
          border: `1px solid ${colors.blueLightMode}`,
          boxShadow: `3px 3px 0px 0px ${colors.primaryHoverBoxShadow}`,
          ":not(:last-of-type)": {
            border: `none`,
            borderRight: "1px solid white",
          },
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
          ":not(:last-of-type)": {
            border: `1px solid ${colors.defaultModalBackdropColor}`,
          },
        },
      },

      groupedContainedSecondary: {
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

        ":not(:last-of-type)": {
          border: `1px solid ${colors.blueLightMode}`,
        },
        "&.Mui-disabled": {
          color: colors.defaultModalBackdropColor,
          backgroundColor: colors.sidebarBarelyPastWhite,
          borderColor: colors.defaultModalBackdropColor,
          ":not(:last-of-type)": {
            border: `1px solid ${colors.defaultModalBackdropColor}`,
          },
        },
      },
      // @ts-ignore
      groupedContainedTertiary: {
        color: colors.sidebarGrey,
        backgroundColor: colors.white,
        border: `1px solid ${colors.sidebarFaintGrey}`,

        ":not(:last-of-type)": {
          border: `1px solid ${colors.sidebarFaintGrey}`,
        },
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
          ":not(:last-of-type)": {
            border: `1px solid ${colors.defaultModalBackdropColor}`,
          },
        },
      },
    },
  },
};

const darkButtonGroup: Partial<Components<Theme>> = {
  MuiButtonGroup: {
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
      },
      groupedContained: {
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

      groupedContainedPrimary: {
        "&.Mui-disabled": {
          color: colors.gray09,
          backgroundColor: colors.gray05,
        },
      },

      groupedContainedSecondary: {
        "&.Mui-disabled": {
          color: colors.gray05,
          backgroundColor: colors.gray02,
        },
        ":not(:last-of-type)": {
          border: `1px solid ${colors.blueLightMode}`,
        },
      },
      // @ts-ignore
      groupedContainedTertiary: {
        "&.Mui-disabled": {
          color: colors.gray05,
          backgroundColor: colors.gray02,
        },
        ":not(:last-of-type)": {
          border: `1px solid ${colors.sidebarFaintGrey}`,
        },
      },
    },
  },
};

const buttonsGroup = (mode: PaletteMode): Components<Theme> => {
  return mode === "dark" ? darkButtonGroup : lightButtonGroup;
};

export default buttonsGroup;
