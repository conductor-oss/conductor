import CodeIcon from "@mui/icons-material/Code";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import { Box, IconButton, ListItemIcon, Menu, MenuItem } from "@mui/material";
import RefreshIcon from "components/icons/RefreshIcon";
import SplitButton from "components/ui/buttons/ConductorSplitButton";
import { useRef, useState } from "react";
import { fieldBorderColor } from "./styles";

export interface RefreshActionsProps {
  onRefresh: () => void;
  onShowCode: () => void;
}

interface RefreshButtonProps extends RefreshActionsProps {
  id?: string;
}

/** Refresh with a Show as code menu, as an outlined split button. */
export const RefreshButton = ({
  id = "refresh-workflow-search-btn",
  onRefresh,
  onShowCode,
}: RefreshButtonProps) => (
  <Box
    sx={{
      display: "flex",
      // Match the search bar's outline rather than the theme's dark outline.
      "& .MuiButtonGroup-grouped": {
        // Same height as the search bar it sits beside.
        height: 40,
        minHeight: 40,
        borderColor: fieldBorderColor,
        color: "text.primary",
        bgcolor: "background.paper",
        "&:hover": { borderColor: fieldBorderColor, bgcolor: "action.hover" },
      },
    }}
  >
    <SplitButton
      id={id}
      variant="outlined"
      startIcon={<RefreshIcon />}
      options={[{ label: "Show as code", onClick: onShowCode }]}
      primaryOnClick={onRefresh}
    >
      Refresh
    </SplitButton>
  </Box>
);

/**
 * The phone version of {@link RefreshButton}: icon buttons sized for touch,
 * shown in the results header where there is no room beside the search bar.
 */
export const RefreshIconActions = ({
  onRefresh,
  onShowCode,
}: RefreshActionsProps) => {
  const anchorRef = useRef<HTMLButtonElement>(null);
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <Box sx={{ display: "flex", alignItems: "center" }}>
      <IconButton
        aria-label="Refresh results"
        onClick={onRefresh}
        sx={{ width: 44, height: 44 }}
      >
        <RefreshIcon />
      </IconButton>
      <IconButton
        ref={anchorRef}
        aria-label="More actions"
        aria-haspopup="menu"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen((open) => !open)}
        sx={{ width: 44, height: 44 }}
      >
        <KeyboardArrowDownIcon fontSize="small" />
      </IconButton>
      <Menu
        anchorEl={anchorRef.current}
        open={menuOpen}
        onClose={() => setMenuOpen(false)}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <MenuItem
          sx={{ minHeight: 44 }}
          onClick={() => {
            setMenuOpen(false);
            onShowCode();
          }}
        >
          <ListItemIcon>
            <CodeIcon fontSize="small" />
          </ListItemIcon>
          Show as code
        </MenuItem>
      </Menu>
    </Box>
  );
};
