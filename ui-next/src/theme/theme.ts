import baseTheme from "./material/baseTheme";
import appBar from "./material/components/appBar";
import paper from "./material/components/paper";
import atoms from "./material/components/atoms";
import buttons from "./material/components/buttons";
import formControls from "./material/components/formControls";
import dropdownsMenusPopovers from "./material/components/dropdownsMenusPopovers";
import modals from "./material/components/modals";
import tables from "./material/components/tables";
import tabs from "./material/components/tabs";

import { PaletteMode } from "@mui/material";

import { ThemeOptions, createTheme } from "@mui/material/styles";
import { getPaletteForMode } from "./material/getPaletteForMode";
import buttonsGroup from "./material/components/buttonsGroup";

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
