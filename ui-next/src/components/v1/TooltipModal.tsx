import {
  Box,
  Theme,
  Tooltip,
  TooltipProps,
  styled,
  useMediaQuery,
} from "@mui/material";
import MuiTypography from "components/MuiTypography";
import { ReactNode } from "react";
import { colors } from "theme/tokens/variables";

const CustomisedTooltip = styled(
  ({ className, ...props }: TooltipProps & { isMobile: boolean }) => (
    <Tooltip {...props} classes={{ popper: className }} />
  ),
)(({ isMobile }) => ({
  zIndex: 1099, // reduced from default (1500) to prevent appearing above AppBar, Drawer, Modal, and Snackbar
  "& .MuiTooltip-tooltip": {
    backgroundColor: "white",
    color: "rgba(6, 6, 6, 1)",
    minWidth: isMobile ? 300 : 600,
    width: "100%",
    filter: "drop-shadow(0px 0px 6px rgba(89, 89, 89, 0.41))",
    borderRadius: "6px",
    border: `2px solid ${colors.darkBlueLightMode}`,
  },
  "& .MuiTooltip-arrow": {
    color: "white",
    fontSize: "28px",
    "&:before": {
      border: `2px solid ${colors.darkBlueLightMode}`,
    },
  },
}));

const tooltipStyle = {
  container: {
    background: "white",
  },
  icon: {
    color: "#9157FF",
    fontSize: "20px",
  },
};

interface TooltipStatelessProps extends Omit<TooltipProps, "content"> {
  title: string;
  content: ReactNode;
  handleOpen?: (value: boolean) => void;
  handleClose?: () => void;
}

export const TooltipModal = ({
  title,
  content,
  children,
  placement,
  open,
  handleClose,
}: TooltipStatelessProps) => {
  const isMobile = useMediaQuery((theme: Theme) =>
    theme.breakpoints.down("sm"),
  );
  return (
    <CustomisedTooltip
      arrow
      isMobile={isMobile}
      placement={placement}
      open={open}
      disableFocusListener
      disableHoverListener
      disableTouchListener
      onClose={handleClose}
      title={
        <Box sx={tooltipStyle.container}>
          <MuiTypography fontWeight={600} fontSize={14}>
            {title}
          </MuiTypography>
          <Box color={colors.greyText} fontWeight="300" fontSize="12px" my={1}>
            {content}
          </Box>
        </Box>
      }
    >
      {children}
    </CustomisedTooltip>
  );
};
