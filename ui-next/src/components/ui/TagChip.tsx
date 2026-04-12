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

const TagChip = forwardRef<HTMLDivElement, ChipProps>(
  ({ style = {}, ...props }, ref) => {
    const combinedStyles = { ...customStyles, ...style };
    return <Chip ref={ref} style={combinedStyles} {...props} />;
  },
);

export default TagChip;
