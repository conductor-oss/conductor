import { SliderProps } from "@mui/material";
import { ChangeEvent, ReactNode } from "react";
type ConductorSliderStatelessProps = SliderProps & {
    label?: string | ReactNode;
    handleInputChange: (event: ChangeEvent<HTMLInputElement>) => void;
    handleBlur: () => void;
    textBox?: boolean;
    sliderColor?: string;
};
declare const ConductorSliderStateless: ({ label, value, min, max, handleBlur, handleInputChange, textBox, sliderColor, ...rest }: ConductorSliderStatelessProps) => import("react").JSX.Element;
export type { ConductorSliderStatelessProps };
export default ConductorSliderStateless;
