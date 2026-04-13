import { SxProps, Theme } from "@mui/material";
import type { ConductorInputStyleProps } from "components/ui/inputs/ConductorInput";
import { fontSizes } from "theme/tokens/variables";
import { getColor } from "./theme";

// Calculate labelScale here to avoid circular dependency
const defaultFontSize = Number(fontSizes.fontSize3.replaceAll("px", "")); // pixel
export const labelScale = 12 / defaultFontSize; // (12px / input's fontSize)

export const baseLabelStyle: SxProps<Theme> = {
  fontSize: fontSizes.fontSize3,
  fontWeight: 200,
  pointerEvents: "auto",
  transform: `translate(12px, -9px) scale(${labelScale})`,
};

export const inputLabelStyle = ({
  theme,
  isFocused,
  error,
  isInputEmpty,
}: ConductorInputStyleProps): SxProps<Theme> => ({
  ...baseLabelStyle,
  color: getColor({ theme, isFocused, error, isLabel: true, isInputEmpty }),
  fontWeight: isFocused ? 500 : 200,

  "&.Mui-disabled": {
    color: theme.palette.label.disabled,
  },
});

export const formHelperStyle = ({
  theme,
  isFocused,
  error,
  isInputEmpty,
}: ConductorInputStyleProps): SxProps<Theme> => ({
  fontSize: `${labelScale}em`,
  color: getColor({ theme, isFocused, error, isLabel: true, isInputEmpty }),
  pl: "8px",
  mt: "4px",

  "&.Mui-disabled": {
    color: theme.palette.input.text,
  },
});
