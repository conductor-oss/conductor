import { Box, useMediaQuery } from "@mui/material";
import { Theme } from "@mui/material/styles";
import ButtonLinks from "components/v1/layout/header/ButtonLinks";
import { SidebarContext } from "components/Sidebar/context/SidebarContext";
import { ReactNode, useContext } from "react";
import { useLocation } from "react-router";
import { checkPathFlag } from "utils/checkPathFlag";

interface SectionHeaderProps {
  title: string;
  actions?: ReactNode;
  // This should be removed once we
  // move the top bar to the shared folder
  // and use the same layout in all apps
  _deprecate_marginTop?: number;
  marginRightForAction?: number;
}
const SIDEBAR_OPEN_BREAKPOINT = 800;
const SIDEBAR_CLOSED_BREAKPOINT = 491;

const SectionHeader = ({
  title,
  actions = null,
  _deprecate_marginTop = 65,
  marginRightForAction = 0,
}: SectionHeaderProps) => {
  const { pathname } = useLocation();
  const { open: isSideBarOpen } = useContext(SidebarContext);
  const featureFlagEnabled = checkPathFlag(pathname);
  const isValidWidth = useMediaQuery((theme: Theme) =>
    theme.breakpoints.down(
      isSideBarOpen ? SIDEBAR_OPEN_BREAKPOINT : SIDEBAR_CLOSED_BREAKPOINT,
    ),
  );
  return (
    <Box
      id="section-header-container"
      sx={{
        display: "flex",
        justifyContent: ["start", "start", "space-between"],
        alignItems: ["start", "end", "center"],
        flexDirection: ["column", isValidWidth ? "column" : "row", "row"],
        padding: 6,
        paddingTop: 4,
        paddingBottom: 2,
        marginTop: _deprecate_marginTop,
        rowGap: 2,
        gap: 1,
      }}
    >
      <Box
        sx={{
          margin: 0,
          fontSize: "20px",
          fontWeight: 700,
          letterSpacing: "-0.03em",
          width: "100%",
          color: "text.primary",
          marginBottom: [0, 0],
          textTransform: "uppercase",
        }}
      >
        {title}
      </Box>
      <Box
        sx={{
          flexShrink: 0,
          width: ["100%", "auto"],
          minHeight: "40px",
          display: "flex",
          // flexDirection: ["column", "column", "row"],
          alignItems: ["stretch", "center", "center"],
          gap: 2,
          paddingRight: featureFlagEnabled ? "0px" : "8px",
          marginRight: marginRightForAction,
          flexDirection: [isValidWidth ? "column" : "row", "row"],
          justifyContent: "end",
          flexWrap: "wrap",
        }}
      >
        <ButtonLinks
          isSideBarOpen={isSideBarOpen}
          sx={{ gridArea: "links" }}
          showDropdownOnly={!featureFlagEnabled}
        />
        {actions != null ? actions : null}
      </Box>
    </Box>
  );
};

export default SectionHeader;
