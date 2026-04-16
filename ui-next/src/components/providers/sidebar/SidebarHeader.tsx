import CloseIcon from "@mui/icons-material/Close";
import SearchIcon from "@mui/icons-material/Search";
import { Box, IconButton, Typography } from "@mui/material";
import { useMemo } from "react";
import { Key } from "ts-key-enum";
import { ClosedLogo } from "./ClosedLogo";
import { OpenedLogo } from "./OpenedLogo";
import { SidebarItem } from "./SidebarItem";
import { MenuItemType } from "./types";

interface SidebarHeaderProps {
  open: boolean;
  isMobile: boolean;
  customLogo?: string;
  toggleMenu?: () => void;
  onSearchClick?: () => void;
}

export const SidebarHeader = ({
  open,
  isMobile,
  customLogo,
  toggleMenu,
  onSearchClick,
}: SidebarHeaderProps) => {
  // Create search item for SidebarItem component
  const searchItem: MenuItemType = useMemo(
    () => ({
      id: "searchItem",
      title: "Search",
      icon: <SearchIcon />,
      shortcuts: ["⌘", "K"],
      hotkeys: `${Key.Meta} + K`,
      handler: onSearchClick,
      hidden: false,
    }),
    [onSearchClick],
  );

  return (
    <Box
      sx={{
        p: 2,
        display: "flex",
        flexDirection: "column",
        alignItems: open ? "stretch" : "center",
        justifyContent: "flex-start",
        minHeight: 60,
        position: "relative",
        gap: 1,
      }}
    >
      {/* Logo or MENU text */}
      {isMobile ? (
        <Box
          sx={{
            width: "100%",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            pl: 4,
            pr: 0,
          }}
        >
          <Typography fontWeight={600} fontSize={20}>
            MENU
          </Typography>
          <IconButton onClick={toggleMenu}>
            <CloseIcon />
          </IconButton>
        </Box>
      ) : (
        <>
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              justifyContent: open ? "flex-start" : "center",
              position: "relative",
              minHeight: "40px",
              height: "40px",
              width: "100%",
            }}
          >
            {open ? (
              <OpenedLogo customLogo={customLogo} />
            ) : (
              <ClosedLogo customLogo={customLogo} />
            )}
          </Box>
          {/* Search item on new line */}
          {onSearchClick && (
            <Box
              sx={{
                width: "100%",
              }}
            >
              <SidebarItem item={searchItem} open={open} />
            </Box>
          )}
        </>
      )}
    </Box>
  );
};
