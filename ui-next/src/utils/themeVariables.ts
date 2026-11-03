import { orkesTheme } from "theme/tokens/orkes-theme";

export const getThemeAsCSSVariables = (): string[] => {
  return (Object.keys(orkesTheme) as Array<keyof typeof orkesTheme>).map(
    (name) => `--${name}: ${orkesTheme[name]};`,
  );
};
