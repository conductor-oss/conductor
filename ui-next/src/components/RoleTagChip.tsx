import { ChipProps } from "@mui/material";
import { displayRoleName, userRoleColorGenerator } from "utils/roles";
import { forwardRef } from "react";
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
        return displayRoleName(label);
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
