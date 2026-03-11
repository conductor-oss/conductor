import { colors } from "theme/tokens/variables";
import { CSSObject } from "@mui/material/styles";

export const hoveringStyle: CSSObject = {
  color: colors.sidebarBlacky,
  backgroundColor: colors.sidebarBarelyPastWhite,
};

export const listItemButtonBaseStyle: CSSObject = {
  display: "flex",
  px: 2.5,
  py: 1,
  borderRadius: "0px 22px 22px 0px",
  transition: "background-color 0.3s ease-in-out",
  ":hover": {
    zIndex: 1,
  },
  ":focus-visible": {
    outline: "none",
  },
};

export const listItemIconBaseStyle: CSSObject = {
  color: "inherit",
  minWidth: 0,
  justifyContent: "center",
  pointerEvents: "none",
};

export const listItemTextBaseStyle: CSSObject = {
  display: "flex",
  alignItems: "center",
  "& .MuiListItemText-primary": {
    fontStyle: "normal",
    fontWeight: 500,
  },
};

export const contentBoxBaseStyle: CSSObject = {
  display: "flex",
  justifyContent: "space-between",
  alignItems: "center",
  width: "100%",
};

export const subItemTextActiveStyle = {
  ".MuiListItemText-primary": {
    color: colors.sidebarBlacky,
    // backgroundColor: colors.sidebarBarelyPastWhite,
    borderRadius: "0 22px 22px 0",
    height: "100%",
    display: "flex",
    alignItems: "center",
    marginLeft: "-11px",
    paddingLeft: "11px",
  },
};
