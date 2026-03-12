import baseTheme from "theme/material/baseTheme";
import appBar from "theme/material/components/appBar";
import atoms from "theme/material/components/atoms";
import dropdownsMenusPopovers from "theme/material/components/dropdownsMenusPopovers";
import modals from "theme/material/components/modals";
import paper from "theme/material/components/paper";
import tables from "theme/material/components/tables";
import tabs from "theme/material/components/tabs";
import buttons from "./material/components/buttons";
import buttonsGroup from "./material/components/buttonsGroup";
import formControls from "./material/components/formControls";

import { PaletteMode } from "@mui/material";
import { ThemeOptions } from "@mui/material/styles";

import { createTheme } from "@mui/material/styles";
import { getPaletteForMode } from "theme/material/getPaletteForMode";
import { ConductorInputStyleProps } from "../ConductorInput";

declare module "@mui/material/Button" {
  interface ButtonPropsColorOverrides {
    tertiary: true;
  }
}
declare module "@mui/material/ButtonGroup" {
  interface ButtonGroupPropsColorOverrides {
    tertiary: true;
  }
}

export const getOverridesForMode = (mode: PaletteMode) => {
  const overrides = {
    components: {
      ...appBar(mode),
      ...paper,
      // the tiniest reusables like Chip, Link, SvgIcon, etc.
      ...atoms(mode),
      // ALL buttons
      ...buttons(mode),
      // button group
      ...buttonsGroup(mode),
      // inputs, checkboxes, radios, textareas, autocomplete, etc.
      ...formControls(mode),
      // all kinds of popovers, dropdowns, toasts, snackbars,
      ...dropdownsMenusPopovers(),
      ...modals(mode),
      ...tables,
      ...tabs(mode),
    },
  };

  return overrides as ThemeOptions;
};

export const getTheme = (mode: PaletteMode = "light") => {
  return createTheme(
    baseTheme,
    getOverridesForMode(mode),
    getPaletteForMode(mode),
  );
};

export default getTheme;

export const LOCAL_STORAGE_DARK_MODE_TOGGLE_KEY = "dark-mode-toggle";

export const getColor = ({
  theme,
  isFocused,
  error,
  isLabel,
  isInputEmpty,
}: ConductorInputStyleProps) => {
  if (error) {
    return theme.palette.input.error;
  }

  if (isFocused) {
    return theme.palette.input.focus;
  }

  if (isLabel) {
    if (isInputEmpty) {
      return theme.palette.input.text;
    }

    return theme.palette.input.label;
  }

  return theme.palette.input.border;
};
