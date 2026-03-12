import React from "react";
import { Tab as RawTab, Tabs as RawTabs } from "@mui/material";
import { colors } from "../theme/tokens/variables";
import { getTheme } from "../theme";

// Override styles for 'Contextual' tabs
const contextualTabStyle = {
  root: {
    color: colors.gray02,
    textTransform: "none",
    height: "38px",
    minHeight: "38px",
    padding: "12px 16px",
    backgroundColor: colors.gray13,
    [getTheme().breakpoints.up("md")]: {
      minWidth: 0,
    },
    width: "auto",
    "&:hover": {
      backgroundColor: colors.grayXLight,
      color: colors.gray02,
    },
  },
  selected: {
    backgroundColor: "white",
    color: colors.black,
    "&:hover": {
      backgroundColor: "white",
      color: colors.black,
    },
  },
  wrapper: {
    width: "auto",
  },
};

const regularTabStyle = {
  root: {
    "& .MuiTab-root": {
      minWidth: "130px",
      fontWeight: "normal",
      fontSize: "14px",
    },
  },
};

const contextualTabsStyle = {
  indicator: {
    height: 0,
  },
  flexContainer: {
    backgroundColor: colors.gray13,
  },
};

export default function Tabs({ contextual, children, ...props }) {
  return (
    <RawTabs
      sx={contextual ? contextualTabsStyle : regularTabStyle}
      indicatorColor="primary"
      {...props}
    >
      {contextual
        ? children.map((child, idx) =>
            React.cloneElement(child, { contextual: true, key: idx }),
          )
        : children}
    </RawTabs>
  );
}

export function Tab({ contextual = null, ...props }) {
  return <RawTab sx={contextual ? contextualTabStyle : null} {...props} />;
}
