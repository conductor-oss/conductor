import { Tab as RawTab, Tabs as RawTabs } from "@mui/material";
import type { TabProps } from "@mui/material/Tab";
import type { TabsProps } from "@mui/material/Tabs";
import React from "react";
import { getTheme } from "../../theme";
import { colors } from "../../theme/tokens/variables";

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

export type TabsOwnProps = TabsProps & {
  contextual?: boolean;
};

export default function Tabs({ contextual, children, ...props }: TabsOwnProps) {
  return (
    <RawTabs
      sx={contextual ? contextualTabsStyle : regularTabStyle}
      indicatorColor="primary"
      {...props}
    >
      {contextual
        ? React.Children.map(children, (child, idx) =>
            React.isValidElement(child)
              ? React.cloneElement(child, {
                  contextual: true,
                  key: child.key ?? idx,
                })
              : child,
          )
        : children}
    </RawTabs>
  );
}

export type TabOwnProps = TabProps & {
  contextual?: boolean | null;
};

export function Tab({ contextual = null, ...props }: TabOwnProps) {
  return <RawTab sx={contextual ? contextualTabStyle : null} {...props} />;
}
