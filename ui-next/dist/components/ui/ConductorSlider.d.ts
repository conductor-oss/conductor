import { SliderProps } from "@mui/material";
import { ReactNode } from "react";
type ConductorSliderProps = SliderProps & {
    label?: string | ReactNode;
    textBox?: boolean;
    onChangeValue: (value: number) => void;
    sliderColor?: string;
};
declare function ConductorSlider({ label, min, max, textBox, value, onChangeValue, sliderColor, ...rest }: ConductorSliderProps): import("react").JSX.Element;
export default ConductorSlider;
export type { ConductorSliderProps };
