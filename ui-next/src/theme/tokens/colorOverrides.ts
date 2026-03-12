import * as colors from "./colors";

const brandAliases = {
  brand00: colors.indigo00,
  brand01: colors.indigo01,
  brand02: colors.indigo02,
  brand03: colors.indigo03,
  brand04: colors.indigo04,
  brand05: colors.indigo05,
  brand06: colors.indigo06,
  brand07: colors.indigo07,
  brand08: colors.indigo08,
  brand09: colors.indigo09,
  brand10: colors.indigo10,
  brand11: colors.indigo11,
  brand12: colors.indigo12,
  brand13: colors.indigo13,
  brand14: colors.indigo14,
};

const brandShortcuts = {
  brand: brandAliases.brand07,
  bgBrand: brandAliases.brand07,
  bgBrandLight: brandAliases.brand09,
  bgBrandDark: brandAliases.brand05,
  brandXLight: colors.indigoXLight,
  brandXXLight: colors.indigoXXLight,
};

const failureAliases = {
  failure: colors.red07,
  failureLight: colors.red09,
  failureDark: colors.red05,
};

export const colorOverrides = {
  ...colors,
  ...brandAliases,
  ...brandShortcuts,
  ...failureAliases,
};
