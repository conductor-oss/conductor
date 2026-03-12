import { orkesTheme } from "theme/tokens/orkes-theme";

export const getThemeAsCSSVariables = (): string[] => {
  return Array.from(Object.keys(orkesTheme)).map((name) => {
    return `--${name}: ${(orkesTheme as any)[name]};`;
  });
};
