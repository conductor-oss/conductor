import { Backdrop, Box, Drawer, alpha, useTheme } from "@mui/material";
import { useMemo, useState } from "react";
import { useAuth } from "components/features/auth";
import { colors } from "theme/tokens/variables";
import { Auth0User } from "types/User";
import { drawerWidthClose, drawerWidthOpen } from "./constants";
import { useSidebarHover } from "./hooks/useSidebarHover";
import { SidebarHeader } from "./SidebarHeader";
import { SidebarMenu } from "./SidebarMenu";
import { SidebarToggleButton } from "./SidebarToggleButton";
import { MenuItemType } from "./types";

interface SidebarProps {
  menuItems: MenuItemType[];
  open?: boolean;
  onToggle?: (open: boolean) => void;
  apiVersion?: string;
  releaseVersion?: string;
  isAnnouncementBannerVisible?: boolean;
  customLogo?: string;
  isMobile?: boolean;
  toggleMenu?: () => void;
  onSearchClick?: () => void;
}

export const Sidebar = ({
  menuItems,
  open: controlledOpen,
  onToggle,
  apiVersion,
  releaseVersion,
  isAnnouncementBannerVisible: _isAnnouncementBannerVisible,
  customLogo,
  isMobile = false,
  toggleMenu,
  onSearchClick,
}: SidebarProps) => {
  const theme = useTheme();
  const [internalOpen, setInternalOpen] = useState(true);
  const { user, logOut, conductorUser, isAuthenticated } = useAuth();
  const [showCopyAlert, setShowCopyAlert] = useState(false);

  const {
    hoveredMenuId,
    getItemRef,
    handleMouseEnter,
    handleMouseLeave,
    handlePopoverMouseEnter,
    handlePopoverMouseLeave,
  } = useSidebarHover();

  // Use controlled state if provided, otherwise use internal state
  const open = controlledOpen !== undefined ? controlledOpen : internalOpen;

  const handleToggle = () => {
    if (toggleMenu) {
      // Use toggleMenu if provided (for controlled state)
      toggleMenu();
    } else if (onToggle) {
      // Use onToggle if provided (takes boolean)
      onToggle(!open);
    } else {
      // Fall back to internal state
      setInternalOpen(!open);
    }
  };

  const [conductorVersion, uiVersion]: string[] = useMemo(
    () => [apiVersion || "latest", releaseVersion || "latest"],
    [apiVersion, releaseVersion],
  );

  const visibleItems = useMemo(
    () => menuItems.filter((item) => !item.hidden),
    [menuItems],
  );

  // Group items into sections
  const sections = useMemo(() => {
    const mainItems: MenuItemType[] = [];

    visibleItems.forEach((item) => {
      if (item.items && item.items.length > 0) {
        // Items with children are their own sections
        mainItems.push(item);
      } else {
        // Regular items go to main
        mainItems.push(item);
      }
    });

    return mainItems;
  }, [visibleItems]);

  const drawerCustomHeight = useMemo(() => {
    if (isMobile) {
      return "100vh";
    } else {
      return _isAnnouncementBannerVisible ? "calc(100vh - 45px)" : "100vh";
    }
  }, [_isAnnouncementBannerVisible, isMobile]);

  const topMarginForChevronIcon = useMemo(() => {
    if (!_isAnnouncementBannerVisible) {
      return open ? "18px" : "50px";
    } else {
      return open ? "63px" : "95px";
    }
  }, [_isAnnouncementBannerVisible, open]);

  const sidebarContent = (
    <Box
      sx={{
        width: open ? drawerWidthOpen : drawerWidthClose,
        minWidth: open ? drawerWidthOpen : drawerWidthClose,
        height: "100%",
        display: "flex",
        flexDirection: "column",
        backgroundColor: theme.palette.background.paper,
        borderRight: `1px solid ${alpha(theme.palette.divider, 0.1)}`,
        transition: "width 0.2s ease-in-out",
        position: "relative",
      }}
    >
      <SidebarHeader
        open={open}
        isMobile={isMobile}
        customLogo={customLogo}
        toggleMenu={toggleMenu}
        onSearchClick={onSearchClick}
      />

      <SidebarMenu
        sections={sections}
        open={open}
        hoveredMenuId={hoveredMenuId}
        getItemRef={getItemRef}
        handleMouseEnter={handleMouseEnter}
        handleMouseLeave={handleMouseLeave}
        handlePopoverMouseEnter={handlePopoverMouseEnter}
        handlePopoverMouseLeave={handlePopoverMouseLeave}
        isAuthenticated={isAuthenticated}
        isMobile={isMobile}
        user={(user as Auth0User) || null}
        conductorUser={conductorUser ? { id: conductorUser.id } : null}
        logOut={logOut}
        conductorVersion={conductorVersion}
        uiVersion={uiVersion}
        showCopyAlert={showCopyAlert}
        setShowCopyAlert={setShowCopyAlert}
      />
    </Box>
  );

  // Wrap in Drawer for mobile, otherwise return as-is
  return (isMobile && open) || !isMobile ? (
    <>
      <Drawer
        anchor={isMobile ? "right" : "left"}
        variant="permanent"
        open={open}
        onClose={toggleMenu}
        sx={{
          "& ::-webkit-scrollbar": {
            display: "none",
          },
          height: drawerCustomHeight,
          ".MuiPaper-root": {
            "&.MuiDrawer-paper": {
              background: isMobile
                ? colors.sidebarBarelyPastWhite
                : "transparent",
              borderRight: `3px solid ${colors.sidebarBarelyPastWhite}`,
            },
            border: "none",
            boxShadow: colors.bgBrandDark,
            position: isMobile ? "auto" : "relative",
          },
        }}
      >
        <SidebarToggleButton
          open={open}
          isMobile={isMobile}
          topMargin={topMarginForChevronIcon}
          onToggle={handleToggle}
        />
        {sidebarContent}
      </Drawer>
      {isMobile && (
        <Backdrop
          sx={{
            zIndex: (theme) => theme.zIndex.drawer - 1,
            backdropFilter: "blur(2px)",
          }}
          open={!!open}
          onClick={toggleMenu}
        />
      )}
    </>
  ) : null;
};

export default Sidebar;
