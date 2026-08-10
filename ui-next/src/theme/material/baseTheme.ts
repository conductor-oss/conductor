import darkScrollbar from "@mui/material/darkScrollbar";
import { Theme, ThemeOptions, createTheme } from "@mui/material/styles";
import _path from "lodash/fp/path";
import { logger } from "utils/logger";
import {
  borders,
  breakpoints,
  fontFamily,
  fontSizes,
  fontWeights,
  lineHeights,
  spacings,
} from "../tokens/variables";

function toNumber(v: string): number {
  return parseFloat(v);
}

const spacingFn = (factor: string | number) => {
  const unit = toNumber(spacings.space0);

  // Support theme.spacing('space3')
  if (typeof factor === "string") {
    const spacingFactor = _path(factor, spacings) as string;
    if (!spacingFactor) {
      logger.warn(`spacingFn: ${factor} is not a valid spacing factor`);
    }
    return toNumber(spacingFactor ?? "0");
  }

  if (typeof factor === "number") {
    // Support theme.spacing(2)
    return unit * factor;
  }

  return unit;
};

const baseThemeOptions: ThemeOptions = {
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
      fontSize: fontSizes.fontSize2,
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
      // Breakpoint to display link buttons on navbar
    },
  },
  shape: {
    borderRadius: toNumber(borders.radiusSmall),
  },
  //color: colorFn,
  spacing: spacingFn,
  components: {
    MuiCssBaseline: {
      styleOverrides: (themeParam: Theme) => ({
        body: themeParam.palette.mode === "dark" ? darkScrollbar() : null,
      }),
    },
  },
};

const baseTheme = createTheme(baseThemeOptions);

export default baseTheme;
