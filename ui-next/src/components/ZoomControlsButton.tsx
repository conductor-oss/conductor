import { Tooltip } from "@mui/material";
import IconButton, {
  MuiIconButtonProps,
} from "components/ui/buttons/MuiIconButton";
import { forwardRef, useContext } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { colors } from "theme/tokens/variables";

type ZoomControlsButtonProps = MuiIconButtonProps & {
  disabled?: boolean;
  tooltip?: string;
};

export const ZoomControlsButton = forwardRef<
  HTMLButtonElement,
  ZoomControlsButtonProps
>(({ style, children, tooltip = "", ...props }, ref) => {
  const { mode } = useContext(ColorModeContext);
  const darkMode = mode === "dark";

  return (
    <Tooltip title={tooltip} arrow>
      <IconButton
        ref={ref}
        disableRipple
        style={{
          display: "flex",
          border: "none",
          background: "none",
          height: "33px",
          width: "40px",
          alignItems: "center",
          justifyContent: "center",
          cursor: "pointer",
          fontSize: "14px",
          color: darkMode ? colors.gray12 : colors.greyText,
          borderRadius: "unset",
          ...style,
        }}
        {...props}
      >
        {children}
      </IconButton>
    </Tooltip>
  );
});
