import { Box, Menu, MenuItem, Theme } from "@mui/material";
import { CaretDown, Plus } from "@phosphor-icons/react";
import { MouseEvent, useState } from "react";

import { Button } from "components";
import MuiTypography from "components/ui/MuiTypography";
import { actionMeta, actionOrder } from "./actionMeta";
import { Action } from "./state/types";

// NB: this theme's spacing unit is 4px, not MUI's default 8px.
const itemStyle = {
  display: "flex",
  alignItems: "flex-start",
  gap: 3,
  px: 4,
  py: 2.5,
  whiteSpace: "normal",
};

const descriptionStyle = {
  display: "block",
  color: (theme: Theme) => theme.palette.text.secondary,
};

/**
 * Replaces the old select-then-click-Add pair: one button opens a menu where
 * each action type is described, so picking one is a single decision.
 */
const AddActionMenu = ({ onAdd }: { onAdd: (action: Action) => void }) => {
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  const close = () => setAnchorEl(null);

  return (
    <>
      <Button
        variant="outlined"
        size="small"
        startIcon={<Plus size={12} />}
        endIcon={<CaretDown size={12} />}
        aria-haspopup="menu"
        aria-expanded={Boolean(anchorEl)}
        onClick={(event: MouseEvent<HTMLElement>) =>
          setAnchorEl(event.currentTarget)
        }
      >
        Add action
      </Button>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={close}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
        slotProps={{ paper: { sx: { width: 320 } } }}
      >
        {actionOrder.map((value) => {
          const { label, description, icon: Icon } = actionMeta[value];
          return (
            <MenuItem
              key={value}
              sx={itemStyle}
              onClick={() => {
                onAdd(value);
                close();
              }}
            >
              <Box sx={{ display: "flex", pt: "2px", color: "primary.main" }}>
                <Icon size={18} />
              </Box>
              <Box>
                <MuiTypography variant="body2">{label}</MuiTypography>
                <MuiTypography variant="caption" sx={descriptionStyle}>
                  {description}
                </MuiTypography>
              </Box>
            </MenuItem>
          );
        })}
      </Menu>
    </>
  );
};

export default AddActionMenu;
