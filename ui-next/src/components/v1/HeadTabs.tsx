import { Tabs, TabsProps } from "@mui/material";
import {
  tabsColor,
  tabActiveColor,
  tabBackground,
  white,
} from "theme/tokens/colors";

const tabsStyle = (pl = "100px") => {
  return {
    ".MuiTabs-scroller": {
      backgroundColor: "transparent",
    },
    ".MuiTabs-flexContainer": {
      background: "transparent",
      paddingLeft: pl,
    },
    ".MuiButtonBase-root": {
      color: tabsColor,
      fontSize: "16px",
      fontWeight: 600,
      background: tabBackground,
      borderRadius: "10px 10px 0px 0px",
      margin: "0 2px",
    },
    ".Mui-selected": {
      color: tabActiveColor,
      background: white,
    },
    ".MuiTabs-indicator": {
      display: "none",
    },
    "&.MuiTabs-root": {
      width: "fit-content",
    },
    "@media(max-width:1025px)": {
      ".MuiTabs-flexContainer": {
        paddingRight: "0px",
        justifyContent: "left",
        paddingLeft: "0px",
      },
    },
  };
};

interface HeadTabsProps extends TabsProps {
  pl?: string;
}

const HeadTabs = ({
  value,
  onChange,
  children,
  pl,
  ...props
}: HeadTabsProps) => {
  return (
    <Tabs {...props} sx={tabsStyle(pl)} value={value} onChange={onChange}>
      {children}
    </Tabs>
  );
};

export type { HeadTabsProps };
export default HeadTabs;
