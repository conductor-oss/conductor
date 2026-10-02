import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import { Box, ButtonBase } from "@mui/material";
import { alpha } from "@mui/material/styles";
import { ReactNode, useRef } from "react";
import { ResponsivePanel } from "./ResponsivePanel";
import { fieldBorderColor, helperTextColor } from "./styles";

export interface FilterPillProps {
  id: string;
  label: string;
  value: string;
  /** Highlights the pill when the filter is narrowed from its default. */
  active?: boolean;
  disabled?: boolean;
  open: boolean;
  onOpen: () => void;
  onClose: () => void;
  panelWidth?: number;
  /** Panel content. Rendered only while open, so drafts reset on every open. */
  children: ReactNode;
}

/**
 * A compact filter trigger showing the applied value. On phones the label sits
 * above the value so three pills fit side by side without scrolling.
 */
export const FilterPill = ({
  id,
  label,
  value,
  active = false,
  disabled = false,
  open,
  onOpen,
  onClose,
  panelWidth,
  children,
}: FilterPillProps) => {
  const anchorRef = useRef<HTMLButtonElement>(null);

  return (
    <>
      <ButtonBase
        id={id}
        ref={anchorRef}
        aria-haspopup="dialog"
        aria-expanded={open}
        disabled={disabled}
        onClick={open ? onClose : onOpen}
        sx={{
          display: "flex",
          flexDirection: { xs: "column", sm: "row" },
          alignItems: { xs: "flex-start", sm: "center" },
          justifyContent: "center",
          gap: { xs: 0.25, sm: 1 },
          width: { xs: "100%", sm: "auto" },
          minWidth: 0,
          minHeight: { xs: 56, sm: 36 },
          px: { xs: 2, sm: 2.5 },
          py: { xs: 1, sm: 0.5 },
          border: 1,
          borderColor: active ? "primary.main" : fieldBorderColor,
          borderRadius: { xs: 2, sm: 4 },
          bgcolor: (theme) =>
            active
              ? alpha(theme.palette.primary.main, 0.08)
              : theme.palette.background.paper,
          color: active ? "primary.dark" : "text.primary",
          fontSize: 13,
          textAlign: "left",
          "&.Mui-disabled": { opacity: 0.55 },
          "&:focus-visible": {
            outline: "2px solid",
            outlineColor: "primary.main",
            outlineOffset: 2,
          },
        }}
      >
        <Box
          component="span"
          sx={{
            fontSize: { xs: 11, sm: 13 },
            color: { xs: helperTextColor, sm: "inherit" },
            whiteSpace: "nowrap",
          }}
        >
          {label}
          <Box component="span" sx={{ display: { xs: "none", sm: "inline" } }}>
            :
          </Box>
        </Box>
        <Box
          component="span"
          sx={{
            maxWidth: "100%",
            fontWeight: 600,
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {value}
        </Box>
        <KeyboardArrowDownIcon
          aria-hidden="true"
          sx={{ display: { xs: "none", sm: "block" }, fontSize: 16 }}
        />
      </ButtonBase>
      <ResponsivePanel
        anchorEl={anchorRef.current}
        open={open}
        onClose={onClose}
        title={label}
        width={panelWidth}
      >
        {children}
      </ResponsivePanel>
    </>
  );
};
