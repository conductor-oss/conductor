import { PaletteMode, Theme } from "@mui/material";
import { Components } from "@mui/material/styles";
export declare const SMALL_INPUT_HEIGHT = "36px";
export declare const inputLabelIdleStyles: {
    transform?: undefined;
    position?: undefined;
    fontWeight?: undefined;
    fontSize?: undefined;
    paddingLeft?: undefined;
    marginBottom?: undefined;
    marginTop?: undefined;
    color?: undefined;
} | {
    transform: string;
    position: string;
    fontWeight: number;
    fontSize: string;
    paddingLeft: number;
    marginBottom: string;
    marginTop: number;
    color: string;
};
export declare const inputLabelFocusedStyles: {
    color: string;
};
declare const formControls: (mode: PaletteMode) => Components<Theme>;
export default formControls;
