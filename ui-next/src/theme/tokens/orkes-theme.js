import Color from "color";

const orkesThemeBase = {
  background: "#AAAAAA",
  primary: "#1976d2",
  // TODO: Define these
  // text: "#333333?",
  // danger: "#??????",
  // success: "#??????",
  // warning: "#??????",
};

const generateColorShades = (name, color) => {
  return {
    [`${name}Darkest`]: Color(color).darken(0.6).hex(),
    [`${name}Darker`]: Color(color).darken(0.4).hex(),
    [`${name}Dark`]: Color(color).darken(0.2).hex(),
    [`${name}Light`]: Color(color).lighten(0.2).hex(),
    [`${name}Lighter`]: Color(color).lighten(0.4).hex(),
    [`${name}Lightest`]: Color(color).lighten(0.6).hex(),
  };
};

const orkesThemeWithShades = Object.entries(orkesThemeBase).reduce(
  (acc, [name, color]) => {
    return {
      ...acc,
      ...generateColorShades(name, color),
    };
  },
  {},
);

export const orkesTheme = {
  ...orkesThemeBase,
  ...orkesThemeWithShades,
};
