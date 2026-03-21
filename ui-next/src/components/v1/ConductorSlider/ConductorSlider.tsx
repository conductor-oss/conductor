import { SliderProps } from "@mui/material";
import { ChangeEvent, ReactNode } from "react";
import ConductorSliderStateless from "./ConductorSliderStateless";

type ConductorSliderProps = SliderProps & {
  label?: string | ReactNode;
  textBox?: boolean;
  onChangeValue: (value: number) => void;
  sliderColor?: string;
};

function ConductorSlider({
  label,
  min,
  max,
  textBox,
  value,
  onChangeValue,
  sliderColor,
  ...rest
}: ConductorSliderProps) {
  const minValue = min ? min : 0;
  const maxValue = max ? max : 100;
  const handleInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    onChangeValue(
      event.target.value === "" ? minValue : Number(event.target.value),
    );
  };

  const handleBlur = () => {
    if (Number(value) < minValue) {
      onChangeValue(minValue);
    } else if (Number(value) > maxValue) {
      onChangeValue(maxValue);
    }
  };
  const handleChange = (_e: Event, value: number | number[]) => {
    onChangeValue(Array.isArray(value) ? value[0] : value);
  };
  return (
    <ConductorSliderStateless
      label={label}
      min={minValue}
      max={maxValue}
      handleInputChange={handleInputChange}
      handleBlur={handleBlur}
      textBox={textBox}
      value={value ? value : minValue}
      onChange={handleChange}
      sliderColor={sliderColor}
      {...rest}
    />
  );
}

export default ConductorSlider;
export type { ConductorSliderProps };
