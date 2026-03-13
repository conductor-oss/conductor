import { Palette, PaletteMode, PaletteOptions } from "@mui/material";
import { ThemeOptions } from "@mui/material/styles";
import { colors } from "theme/tokens/variables";
import { FEATURES, featureFlags } from "utils/flags";

// TODO: get rid of these components after applying new inputs whole app
const enabledWhiteBackgroundForm = featureFlags.isEnabled(
  FEATURES.ENABLE_WHITE_BACKGROUND_FORM,
);

export const lightModePalette: Partial<PaletteOptions> = {
  primary: {
    main: colors.primary,
    light: colors.bgBrandLight,
    dark: colors.bgBrandDark,
    contrastText: colors.white,
  },
  success: {
    main: colors.successTag,
    light: colors.green08,
    dark: colors.green04,
    contrastText: colors.white,
  },
  warning: {
    main: colors.orange07,
    light: colors.orange08,
    dark: colors.orange06,
    contrastText: colors.white,
  },
  secondary: {
    main: colors.gray12,
    light: colors.gray14,
    dark: colors.gray10,
    contrastText: colors.gray00,
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
  // Custom from here
  purple: {
    main: colors.purple,
    light: colors.lightPurple,
  },
  pink: {
    main: colors.errorTag,
  },
  faintGrey: colors.sidebarFaintGrey,
  tertiary: {
    main: colors.white,
    light: colors.white,
    dark: colors.white,
    contrastText: colors.sidebarGrey,
  },
  blue: {
    main: colors.blueLight,
    light: colors.blueLightMode,
    dark: colors.blueLightMode,
    contrastText: colors.blueLightMode,
  },
  input: {
    text: colors.sidebarBlacky,
    label: colors.sidebarUIVersion,
    border: colors.greyText2,
    focus: colors.blueLightMode,
    error: colors.errorRed,
    background: colors.white,
    disabled: colors.greyText,
  },
  label: {
    text: colors.sidebarGrey,
    disabled: colors.greyText,
  },
  green: {
    primary: colors.primaryGreen,
  },
  customBackground: {
    main: colors.sidebarBarelyPastWhite,
    form: enabledWhiteBackgroundForm ? colors.white : colors.gray14,
  },
};

export const darkModePalette: Partial<Palette> = {
  // Dark mode colors
  primary: {
    main: colors.primary,
    light: colors.bgBrandLight,
    dark: colors.bgBrandDark,
    contrastText: colors.white,
  },
  success: {
    main: colors.green06,
    light: colors.green06,
    dark: colors.green06,
    contrastText: colors.white,
  },
  warning: {
    main: colors.orange06,
    light: colors.orange06,
    dark: colors.orange06,
    contrastText: colors.white,
  },
  secondary: {
    main: colors.gray04,
    light: colors.gray06,
    dark: colors.gray08,
    contrastText: colors.gray14,
  },
  text: {
    primary: colors.gray14,
    secondary: colors.gray12,
    disabled: colors.gray09,
    hint: colors.gray09,
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
    paper: colors.gray01,
    default: colors.black,
  },
  divider: colors.blackXXLight,
  // Custom from here
  purple: {
    main: colors.purple,
    light: colors.lightPurple,
  },
  pink: {
    main: colors.errorTag,
  },
  faintGrey: colors.sidebarFaintGrey,
  tertiary: {
    main: colors.white,
    light: colors.white,
    dark: colors.white,
    contrastText: colors.sidebarGrey,
  },
  blue: {
    main: colors.blueLight,
    light: colors.blueLightMode,
    dark: colors.blueLightMode,
    contrastText: colors.blueLightMode,
  },
  input: {
    text: colors.sidebarBlacky,
    label: colors.sidebarGrey,
    border: colors.greyText2,
    focus: colors.blueLight,
    error: colors.errorRed,
    background: colors.white,
    disabled: colors.greyText,
  },
  label: {
    text: colors.sidebarGrey,
    disabled: colors.greyText,
  },
  green: {
    primary: colors.primaryGreen,
  },
  customBackground: {
    main: colors.sidebarBarelyPastWhite,
    form: enabledWhiteBackgroundForm ? colors.white : colors.gray00,
  },
};

export const getPaletteForMode = (mode: PaletteMode): ThemeOptions => {
  return {
    palette: {
      mode,
      ...(mode === "light" ? lightModePalette : darkModePalette),
    },
  };
};
