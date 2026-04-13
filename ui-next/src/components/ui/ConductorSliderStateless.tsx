import { Box, Slider, SliderProps, TextField, styled } from "@mui/material";
import { ChangeEvent, ReactNode } from "react";
import { blueLightMode, greyText } from "theme/tokens/colors";

const DEFAULT_SLIDER_COLOR = blueLightMode;

const CustomSlider = styled(Slider, {
  shouldForwardProp: (prop) => prop !== "sliderColor",
})<{ sliderColor?: string }>(({ sliderColor = DEFAULT_SLIDER_COLOR }) => ({
  height: "1px",
  "& .MuiSlider-track": {
    backgroundColor: sliderColor,
    border: "0px",
  },
  "& .MuiSlider-rail": {
    backgroundColor: "#DDD",
  },
  "& .MuiSlider-thumb": {
    backgroundColor: sliderColor,
    width: "17px",
    height: "17px",
  },
}));

type ConductorSliderStatelessProps = SliderProps & {
  label?: string | ReactNode;
  handleInputChange: (event: ChangeEvent<HTMLInputElement>) => void;
  handleBlur: () => void;
  textBox?: boolean;
  sliderColor?: string;
};

const labelStyle = {
  color: greyText,
  fontSize: "12px",
  fontWeight: 300,
};

const ConductorSliderStateless = ({
  label,
  value,
  min,
  max,
  handleBlur,
  handleInputChange,
  textBox,
  sliderColor = DEFAULT_SLIDER_COLOR,
  ...rest
}: ConductorSliderStatelessProps) => {
  const textFieldStyle = {
    marginLeft: "10px",
    "& .MuiOutlinedInput-root": {
      "&.Mui-focused fieldset": {
        borderColor: sliderColor,
        borderWidth: "1px",
      },
    },
  };

  return (
    <Box>
      <Box display="flex" justifyContent="space-between">
        {label && <Box sx={labelStyle}>{label}</Box>}
        {textBox && (
          <TextField
            sx={textFieldStyle}
            value={value?.toString()}
            size="small"
            type="number"
            onChange={handleInputChange}
            onBlur={handleBlur}
            inputProps={{
              min: min,
              max: max,
              type: "number",
              "aria-labelledby": "input-slider",
              style: {
                textAlign: "center",
                width: "30px",
                fontWeight: 600,
              },
            }}
          />
        )}
      </Box>

      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          px: 2,
        }}
      >
        <CustomSlider
          value={value}
          min={min}
          max={max}
          sliderColor={sliderColor}
          {...rest}
        />
      </Box>
    </Box>
  );
};

export type { ConductorSliderStatelessProps };
export default ConductorSliderStateless;
