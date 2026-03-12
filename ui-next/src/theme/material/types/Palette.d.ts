import "@mui/material/styles";

interface CustomPalette {
  purple: {
    main: string;
    light: string;
  };
  pink: {
    main: string;
  };
  faintGrey: string;
  tertiary: {
    main: string;
    light: string;
    dark: string;
    contrastText: string;
  };
  blue: {
    main: string;
    light: string;
    dark: string;
    contrastText: string;
  };
  input: {
    text: string;
    label: string;
    border: string;
    focus: string;
    error: string;
    background: string;
    disabled: string;
  };
  label: {
    text: string;
    disabled: string;
  };
  green: {
    primary: string;
  };
  customBackground: {
    main: string;
    form: string;
  };
}

declare module "@mui/material/styles" {
  interface TypeText {
    hint: string;
  }
  interface TypeTextOptions {
    hint?: string;
  }

  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface Palette extends CustomPalette {}

  interface PaletteOptions {
    text?: Partial<TypeText>;
    input?: Partial<CustomPalette["input"]>;
    purple?: Partial<CustomPalette["purple"]>;
    pink?: Partial<CustomPalette["pink"]>;
    faintGrey?: string;
    tertiary?: Partial<CustomPalette["tertiary"]>;
    blue?: Partial<CustomPalette["blue"]>;
    label?: Partial<CustomPalette["label"]>;
    green?: Partial<CustomPalette["green"]>;
    customBackground?: Partial<CustomPalette["customBackground"]>;
  }
}
