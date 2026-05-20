import Slider from "@mui/material/Slider";
import { styled } from "@mui/material/styles";
import _nth from "lodash/nth";

const CustomSlider = styled(Slider)((props) => {
  // used for switching the tooltip/label position
  const min = props.min ?? 0;
  const max = props.max ?? 0;
  const mid = (min + max) / 2;
  const currentMinVal = _nth(props?.value as number[], 0) ?? min;
  const currentMaxVal = _nth(props?.value as number[], 1) ?? max;
  //
  return {
    "& .MuiSlider-thumb": {
      transition: "none",
    },
    "& .MuiSlider-track": {
      transition: "none",
    },

    "& .MuiSlider-markLabel": {
      color: "primary.contrastText",
      fontSize: "0.75rem",
    },
    '&.MuiSlider-root .MuiSlider-thumb[data-index="0"] .MuiSlider-valueLabel': {
      ...(currentMinVal < mid
        ? { marginLeft: "166px" }
        : { marginRight: "166px" }),

      "&::before": {
        left: currentMinVal < mid ? "12px" : "calc(100% - 12px)",
      },
    },
    '&.MuiSlider-root .MuiSlider-thumb[data-index="1"] .MuiSlider-valueLabel': {
      ...(currentMaxVal > mid
        ? { marginRight: "166px" }
        : { marginLeft: "166px" }),
      "&::before": {
        left: currentMaxVal > mid ? "calc(100% - 12px)" : "12px",
      },
    },
  };
});

export default CustomSlider;
