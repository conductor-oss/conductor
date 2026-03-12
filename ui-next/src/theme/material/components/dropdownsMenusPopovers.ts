import { fontSizes, lineHeights } from "../../tokens/variables";
import { Theme } from "@mui/material";
import { Components } from "@mui/material/styles";

const dropdownsMenusAndPopovers = (): Components<Theme> => {
  return {
    MuiMenu: {
      defaultProps: {
        transitionDuration: 0,
        elevation: 3,
      },
      // styleOverrides: {
      //   dense: {
      //     paddingTop: 0,
      //     paddingBottom: 0,
      //   },
      // },
    },
    MuiPopover: {
      defaultProps: {
        elevation: 3,
      },
    },
    MuiPopper: {
      //@ts-ignore
      styleOverrides: {
        root: {
          zIndex: 90000,
        },
      },
    },
    MuiMenuItem: {
      styleOverrides: {
        root: {
          fontSize: fontSizes.fontSize1,
        },
        dense: {
          paddingTop: 0,
          paddingBottom: 0,
        },
      },
    },
    MuiListItemText: {
      styleOverrides: {
        secondary: {
          fontSize: fontSizes.fontSize1,
        },
        primary: {
          fontSize: fontSizes.fontSize1,
        },
      },
    },
    MuiListSubheader: {
      styleOverrides: {
        root: ({ theme }) => ({
          fontSize: fontSizes.fontSize2,
          lineHeight: lineHeights.lineHeight1,
          paddingTop: theme.spacing(1),
          paddingBottom: theme.spacing(1),
        }),
      },
    },
    MuiSnackbarContent: {
      styleOverrides: {
        root: ({ theme }) => ({
          backgroundColor: theme.palette.primary.main,
          paddingTop: 0,
          paddingBottom: 0,
          marginRight: theme.spacing(4),
          marginLeft: theme.spacing(4),
          borderRadius: theme.shape.borderRadius,
          boxShadow: "none",
        }),
        action: ({ theme }) => ({
          "& button": {
            color: theme.palette.common.white,
          },
        }),
      },
    },
  };
};

export default dropdownsMenusAndPopovers;
