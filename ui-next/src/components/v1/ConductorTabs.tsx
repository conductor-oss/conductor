import { Box, Tabs, TabsProps } from "@mui/material";
import { blueLightMode, greyText2 } from "theme/tokens/colors";

const tabsStyle = {
  height: "40px",
  minHeight: "40px",
  ".MuiButtonBase-root": {
    padding: "0px",
  },
  ".MuiTab-root": {
    fontSize: "12px",
    fontWeight: 500,
    color: greyText2,
    minWidth: "80px",
  },
  "& .MuiTabs-indicator": {
    display: "flex",
    justifyContent: "center",
    backgroundColor: "transparent",
  },
  "& .MuiTabs-indicatorSpan": {
    maxWidth: 40,
    width: "100%",
    backgroundColor: blueLightMode,
  },

  "& .MuiTab-root.Mui-selected": {
    color: blueLightMode,
  },
  ".MuiTabs-scrollButtons.Mui-disabled": {
    opacity: 0.3,
  },
};

type ConductorTabsProps = TabsProps;

const ConductorTabs = ({
  value,
  onChange,
  children,
  ...props
}: ConductorTabsProps) => {
  return (
    <Box sx={{ borderBottom: 0.5, borderColor: "divider" }}>
      <Tabs
        {...props}
        sx={tabsStyle}
        value={value}
        onChange={onChange}
        TabIndicatorProps={{
          children: <span className="MuiTabs-indicatorSpan" />,
        }}
      >
        {children}
      </Tabs>
    </Box>
  );
};

export type { ConductorTabsProps };
export default ConductorTabs;
