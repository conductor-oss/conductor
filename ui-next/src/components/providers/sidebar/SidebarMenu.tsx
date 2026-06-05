import { Box, List, Tooltip, alpha, useTheme } from "@mui/material";
import { ReactNode, RefObject } from "react";
import { Auth0User } from "types/User";
import { SidebarItem } from "./SidebarItem";
import { SidebarFooter } from "./SidebarFooter";
import { MenuItemType } from "./types";

interface SidebarMenuProps {
  sections: MenuItemType[];
  open: boolean;
  hoveredMenuId: string | null;
  getItemRef: (itemId: string) => RefObject<HTMLElement | null>;
  handleMouseEnter: (itemId: string) => () => void;
  handleMouseLeave: () => void;
  handlePopoverMouseEnter: () => void;
  handlePopoverMouseLeave: () => void;
  // Footer props
  isAuthenticated: boolean;
  isMobile: boolean;
  user: Auth0User | null;
  conductorUser: { id: string } | null;
  logOut?: () => void;
  conductorVersion: string;
  uiVersion: string;
  showCopyAlert: boolean;
  setShowCopyAlert: (show: boolean) => void;
  /** When provided (e.g. by enterprise), used for the user block so auth comes from host app; version block still shown. */
  customUserBlock?: ReactNode;
}

export const SidebarMenu = ({
  sections,
  open,
  hoveredMenuId,
  getItemRef,
  handleMouseEnter,
  handleMouseLeave,
  handlePopoverMouseEnter,
  handlePopoverMouseLeave,
  isAuthenticated,
  isMobile,
  user,
  conductorUser,
  logOut,
  conductorVersion,
  uiVersion,
  showCopyAlert,
  setShowCopyAlert,
  customUserBlock,
}: SidebarMenuProps) => {
  const theme = useTheme();

  return (
    <Box
      sx={{
        flex: 1,
        overflowY: "auto",
        overflowX: "hidden",
        px: open ? 2 : 0.5,
        py: 2,
        display: "flex",
        flexDirection: "column",
        "&::-webkit-scrollbar": {
          width: 6,
        },
        "&::-webkit-scrollbar-track": {
          background: "transparent",
        },
        "&::-webkit-scrollbar-thumb": {
          background: alpha(theme.palette.text.secondary, 0.2),
          borderRadius: 3,
          "&:hover": {
            background: alpha(theme.palette.text.secondary, 0.3),
          },
        },
      }}
    >
      <List component="nav" disablePadding sx={{ flex: 1 }}>
        {sections.map((item) => {
          const hasChildren = item.items && item.items.length > 0;
          const itemRef = hasChildren ? getItemRef(item.id) : undefined;

          return (
            <Box key={item.id} sx={{ position: "relative" }}>
              {open ? (
                <SidebarItem
                  item={item}
                  open={open}
                  hoveredMenuId={hoveredMenuId}
                  onMouseEnter={handleMouseEnter(item.id)}
                  onMouseLeave={handleMouseLeave}
                  onPopoverMouseEnter={handlePopoverMouseEnter}
                  onPopoverMouseLeave={handlePopoverMouseLeave}
                  itemRef={itemRef}
                />
              ) : (
                <Tooltip
                  title={item.title}
                  placement="right"
                  arrow
                  open={hasChildren && hoveredMenuId === item.id}
                >
                  <Box
                    onMouseEnter={handleMouseEnter(item.id)}
                    onMouseLeave={handleMouseLeave}
                  >
                    <SidebarItem
                      item={item}
                      open={open}
                      hoveredMenuId={hoveredMenuId}
                      onPopoverMouseEnter={handlePopoverMouseEnter}
                      onPopoverMouseLeave={handlePopoverMouseLeave}
                      itemRef={itemRef}
                    />
                  </Box>
                </Tooltip>
              )}
            </Box>
          );
        })}
      </List>
      <SidebarFooter
        open={open}
        isAuthenticated={isAuthenticated}
        isMobile={isMobile}
        user={user}
        conductorUser={conductorUser}
        logOut={logOut}
        conductorVersion={conductorVersion}
        uiVersion={uiVersion}
        showCopyAlert={showCopyAlert}
        setShowCopyAlert={setShowCopyAlert}
        customUserBlock={customUserBlock}
      />
    </Box>
  );
};
