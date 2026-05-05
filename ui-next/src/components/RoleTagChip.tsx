import { ChipProps } from "@mui/material";
import { userRoleColorGenerator } from "utils/roles";
import { forwardRef } from "react";
import { toUpperFirst } from "utils";
import TagChip from "./ui/TagChip";

const RoleTagChip = forwardRef<HTMLDivElement, ChipProps>(
  ({ style = {}, label = "", ...props }, ref) => {
    let combinedStyles;
    if (typeof label === "string") {
      combinedStyles = {
        ...userRoleColorGenerator(label),
        ...style,
      };
    } else {
      combinedStyles = { ...style };
    }
    const formattedLabel = () => {
      if (typeof label === "string") {
        return toUpperFirst(label);
      }

      return label;
    };
    return (
      <TagChip
        ref={ref}
        style={combinedStyles}
        label={formattedLabel()}
        {...props}
      />
    );
  },
);

export default RoleTagChip;
