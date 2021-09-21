import React from "react";
import { Tabs as RawTabs, Tab as RawTab } from "@material-ui/core";
import { makeStyles } from "@material-ui/styles";
import { colors } from "../theme/variables";
import { theme } from "../theme";

// Override styles for 'Contextual' tabs
const useContextualTabStyles = makeStyles({
  root: {
    color: colors.gray02,
    textTransform: "none",
    height: 38,
    minHeight: 38,
    padding: "12px 16px",
    backgroundColor: colors.gray13,
    [theme.breakpoints.up("md")]: {
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
});

const useContextualTabsStyles = makeStyles({
  indicator: {
    height: 0,
  },
  flexContainer: {
    backgroundColor: colors.gray13,
  },
});

export default function Tabs({ contextual, children, ...props }) {
  const classes = useContextualTabsStyles();
  return (
    <RawTabs
      classes={contextual ? classes : null}
      indicatorColor="primary"
      {...props}
    >
      {contextual
        ? children.map((child, idx) =>
            React.cloneElement(child, { contextual: true, key: idx })
          )
        : children}
    </RawTabs>
  );
}

export function Tab({ contextual, ...props }) {
  const classes = useContextualTabStyles();
  return <RawTab classes={contextual ? classes : null} {...props} />;
}
