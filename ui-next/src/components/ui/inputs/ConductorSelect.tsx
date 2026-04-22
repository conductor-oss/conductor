import { MenuItem, Button, Menu } from "@mui/material";
import { ReactNode, useState, MouseEvent } from "react";
import ConductorInput, { ConductorInputProps } from "./ConductorInput";
import TagIcon from "@mui/icons-material/Tag";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";

import { styled } from "@mui/material/styles";

export type SelectItemType =
  | { label: string; value: string | number }
  | string
  | number;

export type ConductorSelectProps = ConductorInputProps & {
  items?: SelectItemType[];
  children?: ReactNode;
};

const renderItems = (items: SelectItemType[]) =>
  Object.values(items).map((item) => {
    if (typeof item === "string" || typeof item === "number") {
      return (
        <MenuItem key={item} value={item}>
          {item}
        </MenuItem>
      );
    }

    return (
      <MenuItem key={item.label} value={item.value}>
        {item.label}
      </MenuItem>
    );
  });

const ConductorSelect = ({
  items,
  children,
  ...props
}: ConductorSelectProps) => {
  return (
    <ConductorInput {...props} select>
      {items ? renderItems(items) : children}
    </ConductorInput>
  );
};

const StyledButton = styled(Button)({
  textTransform: "none",
  padding: "4px 8px",
  backgroundColor: "#f0f0f0",
  color: "#1976d2",
  minHeight: "28px",
  border: "none",
  "&:hover": {
    backgroundColor: "#e0e0e0",
    border: "none",
  },
  "& .MuiButton-startIcon, & .MuiButton-endIcon": {
    color: "#666",
  },
  "&.MuiButton-outlined": {
    border: "none",
  },
});

const IconCircleWrapper = styled("div")(({ theme }) => ({
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  border: `1px solid ${theme.palette.primary.main}`,
  borderRadius: "50%",
  width: "16px",
  height: "16px",
  padding: "4px",
}));

export type HeadBarSelectProps = {
  items?: SelectItemType[];
  children?: ReactNode;
  value?: string | number;
  onChange?: (value: string) => void;
  label?: string;
  fullWidth?: boolean;
  labelOnEmpty?: string;
};

const HeadBarSelect = ({
  items,
  children,
  value,
  onChange,
  label,
  fullWidth = true,
  labelOnEmpty = "Select",
}: HeadBarSelectProps) => {
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);
  const hasOptions = (items && items.length > 0) || children;

  const handleClick = (event: MouseEvent<HTMLElement>) => {
    if (hasOptions) {
      setAnchorEl(event.currentTarget);
    }
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleMenuItemClick = (newValue: string) => {
    onChange?.(newValue);
    handleClose();
  };

  const displayValue =
    label && value ? `${label} ${value}` : labelOnEmpty || value || "Select";

  return (
    <>
      <StyledButton
        fullWidth={fullWidth}
        onClick={handleClick}
        startIcon={
          <IconCircleWrapper>
            <TagIcon color="primary" sx={{ width: 16, height: 16 }} />
          </IconCircleWrapper>
        }
        endIcon={hasOptions ? <KeyboardArrowDownIcon color="primary" /> : null}
        aria-controls={open ? "head-bar-menu" : undefined}
        aria-haspopup={hasOptions ? "true" : undefined}
        aria-expanded={open ? "true" : undefined}
        variant="outlined"
      >
        {displayValue}
      </StyledButton>
      <Menu
        id="head-bar-menu"
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "left",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "left",
        }}
        PaperProps={{
          style: {
            width: anchorEl?.offsetWidth,
            marginTop: 4,
          },
        }}
        MenuListProps={{
          "aria-labelledby": "head-bar-button",
        }}
      >
        {items
          ? items.map((item) => {
              const itemValue = typeof item === "object" ? item.value : item;
              const itemLabel = typeof item === "object" ? item.label : item;

              return (
                <MenuItem
                  key={itemValue}
                  onClick={() => handleMenuItemClick(itemValue as string)}
                  selected={value === itemValue}
                >
                  {itemLabel}
                </MenuItem>
              );
            })
          : children}
      </Menu>
    </>
  );
};

export { ConductorSelect, HeadBarSelect };
export default ConductorSelect;
