import { Chip, ChipProps } from "@mui/material";
import { forwardRef } from "react";
import { colors } from "theme/tokens/variables";

const customStyles = {
  background: colors.otherTag,
  color: "black",
  fontWeight: 400,
  borderRadius: "100px",
  fontSize: "12px",
};

const sharedStyles = {
  fontWeight: customStyles.fontWeight,
  borderRadius: customStyles.borderRadius,
  fontSize: customStyles.fontSize,
};

const TagChip = forwardRef<HTMLDivElement, ChipProps>(
  ({ style = {}, color, ...props }, ref) => {
    const combinedStyles = color
      ? { ...sharedStyles, ...style }
      : { ...customStyles, ...style };
    return <Chip ref={ref} style={combinedStyles} color={color} {...props} />;
  },
);

export default TagChip;
