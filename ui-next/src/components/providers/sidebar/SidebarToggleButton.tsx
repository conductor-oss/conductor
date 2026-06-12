import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { IconButton, Tooltip, alpha, useTheme } from "@mui/material";
import { drawerWidthClose, drawerWidthOpen } from "./constants";

interface SidebarToggleButtonProps {
  open: boolean;
  isMobile: boolean;
  topMargin: string;
  onToggle: () => void;
}

export const SidebarToggleButton = ({
  open,
  isMobile,
  topMargin,
  onToggle,
}: SidebarToggleButtonProps) => {
  const theme = useTheme();

  if (isMobile) return null;

  return (
    <Tooltip
      title={open ? "Collapse sidebar" : "Expand sidebar"}
      placement="right"
      arrow
    >
      <IconButton
        onClick={onToggle}
        sx={{
          position: "fixed",
          left: `${(open ? drawerWidthOpen - 20 - 4 : drawerWidthClose) - 16}px`,
          top: `${open ? topMargin : "34px"}`,
          border: `0.5px solid ${alpha(theme.palette.divider, 0.2)}`,
          boxShadow: "4px 4px 10px rgba(89, 89, 89, 0.41)",
          p: 1,
          transition: "all 0.3s ease",
          backgroundColor: theme.palette.background.paper,
          color: theme.palette.text.secondary,
          zIndex: 1300,
          width: 24,
          height: 24,
          "&:hover": {
            backgroundColor: alpha(theme.palette.action.hover, 0.08),
            color: theme.palette.text.primary,
          },
        }}
      >
        {open ? <ChevronLeftIcon /> : <ChevronRightIcon />}
      </IconButton>
    </Tooltip>
  );
};
