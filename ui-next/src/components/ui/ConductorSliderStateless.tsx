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
    width: "14px",
    height: "14px",
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
  lineHeight: 1.2,
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
    flexShrink: 0,
    "& .MuiOutlinedInput-root": {
      height: "22px",
      "&.Mui-focused fieldset": {
        borderColor: sliderColor,
        borderWidth: "1px",
      },
    },
    "& .MuiInputBase-input": {
      padding: "1px 4px",
      fontSize: "0.75rem",
    },
  };

  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 1.5,
        py: 0.5,
      }}
    >
      {label && (
        <Box sx={{ ...labelStyle, minWidth: "100px", flexShrink: 0 }}>
          {label}
        </Box>
      )}
      <Box sx={{ flex: 1, px: 0.5 }}>
        <CustomSlider
          value={value}
          min={min}
          max={max}
          sliderColor={sliderColor}
          {...rest}
        />
      </Box>
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
  );
};

export type { ConductorSliderStatelessProps };
export default ConductorSliderStateless;
