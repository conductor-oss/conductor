import { Box, Tooltip, TooltipProps, styled } from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import MuiTypography from "components/ui/MuiTypography";
import { greyText } from "theme/tokens/colors";
import { ReactNode } from "react";

const CustomisedTooltip = styled(({ className, ...props }: TooltipProps) => (
  <Tooltip {...props} classes={{ popper: className }} />
))(() => ({
  "& 	.MuiTooltip-tooltip": {
    backgroundColor: "white",
    color: "rgba(6, 6, 6, 1)",
    maxWidth: 450,
    filter: "drop-shadow(0px 0px 6px rgba(89, 89, 89, 0.41))",
    borderRadius: "6px",
  },
  "& 	.MuiTooltip-arrow": {
    color: "white",
    fontSize: "28px",
  },
}));

const tooltipStyle = {
  container: {
    background: "white",
    padding: "10px 30px 5px 30px",
  },
  icon: {
    color: "#9157FF",
    fontSize: "20px",
  },
};

interface TooltipStatelessProps extends Omit<TooltipProps, "content"> {
  title: string;
  content: ReactNode;
  handleOpen: (value: boolean) => void;
  handleClose: () => void;
}

const TooltipStateless = ({
  title,
  content,
  children,
  placement,
  open,
  handleOpen,
  handleClose,
}: TooltipStatelessProps) => {
  return (
    <CustomisedTooltip
      arrow
      placement={placement}
      open={open}
      onOpen={() => handleOpen(true)}
      onClose={handleClose}
      TransitionProps={{ timeout: 500 }}
      title={
        <Box sx={tooltipStyle.container}>
          <MuiTypography position="absolute" left="10px" top="15px">
            <InfoOutlinedIcon sx={tooltipStyle.icon} />
          </MuiTypography>
          <MuiTypography fontWeight={600} fontSize={14}>
            {title}
          </MuiTypography>
          <div
            style={{
              fontWeight: 300,
              color: greyText,
              fontSize: "12px",
              margin: "4px 0",
            }}
          >
            {content}
          </div>
        </Box>
      }
    >
      {children}
    </CustomisedTooltip>
  );
};

export type { TooltipStatelessProps };
export default TooltipStateless;
