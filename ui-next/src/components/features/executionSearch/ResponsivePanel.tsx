import { Box, Drawer, Popover, Typography } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import useMediaQuery from "@mui/material/useMediaQuery";
import { ReactNode } from "react";
import { fieldBorderColor } from "./styles";

export interface ResponsivePanelProps {
  anchorEl: HTMLElement | null;
  open: boolean;
  onClose: () => void;
  /** Accessible name; also shown as the sheet heading on small screens. */
  title: string;
  width?: number;
  children: ReactNode;
}

/**
 * A dropdown panel anchored to its trigger on larger screens and a bottom sheet
 * on phones, so filter menus never overflow a narrow viewport.
 */
export const ResponsivePanel = ({
  anchorEl,
  open,
  onClose,
  title,
  width = 320,
  children,
}: ResponsivePanelProps) => {
  const theme = useTheme();
  const isPhone = useMediaQuery(theme.breakpoints.down("sm"));

  if (isPhone) {
    return (
      <Drawer
        anchor="bottom"
        open={open}
        onClose={onClose}
        slotProps={{
          paper: {
            role: "dialog",
            "aria-label": title,
            sx: {
              borderRadius: "16px 16px 0 0",
              maxHeight: "85vh",
              pb: "env(safe-area-inset-bottom)",
            },
          },
        }}
      >
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 1.25,
            px: 5,
            pt: 2,
            pb: 1,
          }}
        >
          <Box
            aria-hidden="true"
            sx={{
              width: 36,
              height: 4,
              borderRadius: 2,
              bgcolor: "divider",
            }}
          />
          <Typography
            component="h2"
            sx={{ alignSelf: "stretch", fontSize: 16, fontWeight: 600 }}
          >
            {title}
          </Typography>
        </Box>
        {children}
      </Drawer>
    );
  }

  return (
    <Popover
      open={open}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
      transformOrigin={{ vertical: "top", horizontal: "left" }}
      slotProps={{
        paper: {
          role: "dialog",
          "aria-label": title,
          sx: { width, mt: 0.75, borderRadius: 2 },
        },
      }}
    >
      {children}
    </Popover>
  );
};

export interface PanelActionsProps {
  onCancel: () => void;
  onApply: () => void;
  applyLabel?: string;
  applyDisabled?: boolean;
  onClear?: () => void;
  clearLabel?: string;
}

/** Footer shared by the filter panels: an optional Clear link, then Cancel and Apply. */
export const PanelActions = ({
  onCancel,
  onApply,
  applyLabel = "Apply",
  applyDisabled = false,
  onClear,
  clearLabel = "Clear",
}: PanelActionsProps) => (
  <Box
    sx={{
      display: "flex",
      alignItems: "center",
      justifyContent: onClear ? "space-between" : "flex-end",
      gap: 1,
      px: 5,
      py: 3,
      borderTop: 1,
      borderColor: "divider",
      "& button": { minHeight: { xs: 44, sm: 36 } },
    }}
  >
    {onClear && (
      <Box
        component="button"
        type="button"
        onClick={onClear}
        sx={{
          border: 0,
          bgcolor: "transparent",
          px: 2,
          font: "inherit",
          fontSize: 13,
          color: "primary.main",
          cursor: "pointer",
        }}
      >
        {clearLabel}
      </Box>
    )}
    <Box sx={{ display: "flex", gap: 1, flex: { xs: 1, sm: "none" } }}>
      <Box
        component="button"
        type="button"
        onClick={onCancel}
        sx={{
          flex: { xs: 1, sm: "none" },
          px: 4,
          border: 1,
          borderColor: fieldBorderColor,
          borderRadius: 1.5,
          bgcolor: "background.paper",
          font: "inherit",
          fontSize: 13,
          fontWeight: 500,
          color: "text.primary",
          cursor: "pointer",
        }}
      >
        Cancel
      </Box>
      <Box
        component="button"
        type="button"
        onClick={onApply}
        disabled={applyDisabled}
        sx={{
          flex: { xs: 1, sm: "none" },
          px: 4,
          border: 0,
          borderRadius: 1.5,
          bgcolor: "primary.main",
          font: "inherit",
          fontSize: 13,
          fontWeight: 500,
          color: "primary.contrastText",
          cursor: "pointer",
          "&:disabled": {
            bgcolor: "action.disabledBackground",
            cursor: "not-allowed",
          },
        }}
      >
        {applyLabel}
      </Box>
    </Box>
  </Box>
);
