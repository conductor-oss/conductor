import { alpha, Theme } from "@mui/material/styles";

/**
 * Outline for the search inputs, pills and outlined buttons. The theme's
 * `divider` is meant for separators and is too faint for controls; this sits
 * a little darker than MUI's own outlined inputs, in light and dark mode.
 */
export const fieldBorderColor = (theme: Theme) =>
  alpha(theme.palette.text.primary, 0.28);

/**
 * Hint and label text. The theme's `text.secondary` is too faint for small
 * text (about 2.7:1); this is about 5.8:1 on the paper background.
 */
export const helperTextColor = (theme: Theme) =>
  alpha(theme.palette.text.primary, 0.64);

/** Matches the placeholder opacity MUI uses for its inputs. */
export const placeholderColor = (theme: Theme) =>
  alpha(theme.palette.text.primary, 0.42);
