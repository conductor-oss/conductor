import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import {
  Box,
  ClickAwayListener,
  Collapse,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Paper,
  Popper,
  alpha,
  useTheme,
} from "@mui/material";
import React, {
  ReactNode,
  createElement,
  useCallback,
  useMemo,
  useRef,
  useState,
} from "react";
import { useHotkeys } from "react-hotkeys-hook";
import { Link, matchPath, useLocation } from "react-router";
import { colors } from "theme/tokens/variables";
import { HOT_KEYS_SIDEBAR } from "utils/constants/common";
import HotKeysButton from "./HotKeysButton";
import { MenuItemType } from "./types";

interface SidebarItemProps {
  item: MenuItemType;
  level?: number;
  open?: boolean;
  isActive?: boolean;
  onItemClick?: (item: MenuItemType) => void;
  hoveredMenuId?: string | null;
  onMouseEnter?: () => void;
  onMouseLeave?: () => void;
  onPopoverMouseEnter?: () => void;
  onPopoverMouseLeave?: () => void;
  itemRef?: React.RefObject<HTMLElement | null>;
}

export const SidebarItem = ({
  item,
  level = 0,
  open = true,
  isActive = false,
  onItemClick,
  hoveredMenuId,
  onMouseEnter,
  onMouseLeave,
  onPopoverMouseEnter,
  onPopoverMouseLeave,
  itemRef,
}: SidebarItemProps) => {
  const location = useLocation();
  const theme = useTheme();

  const hasChildren = item.items && item.items.length > 0;
  const isRouteActive = useMemo(() => {
    if (item.linkTo && location.pathname === item.linkTo) return true;
    if (item.activeRoutes) {
      return item.activeRoutes.some((route) =>
        matchPath({ path: route, end: true }, location.pathname),
      );
    }
    return false;
  }, [item.linkTo, item.activeRoutes, location.pathname]);

  const visibleChildren = useMemo(
    () => item.items?.filter((child) => !child.hidden) || [],
    [item.items],
  );

  // Auto-expand if any child is active
  const hasActiveChild = useMemo(() => {
    return visibleChildren.some((child) => {
      if (child.linkTo && location.pathname === child.linkTo) return true;
      if (child.activeRoutes) {
        return child.activeRoutes.some((route) =>
          matchPath({ path: route, end: true }, location.pathname),
        );
      }
      return false;
    });
  }, [visibleChildren, location.pathname]);

  // Initialize expanded state - all menus default expanded
  const [isExpanded, setIsExpanded] = useState(true);

  // Update expanded state when active child changes
  const prevHasActiveChildRef = useRef(hasActiveChild);

  if (hasActiveChild && !prevHasActiveChildRef.current && !isExpanded) {
    setIsExpanded(true);
  }
  prevHasActiveChildRef.current = hasActiveChild;

  // Parent items should show as active if any child is active
  const active = Boolean(
    isActive || isRouteActive || (hasChildren && hasActiveChild),
  );
  // Check if this is a parent with an active child (not directly active)
  const isParentWithActiveChild =
    hasChildren && hasActiveChild && !isRouteActive && !isActive;

  const handleClick = useCallback(() => {
    if (hasChildren) {
      setIsExpanded((prev) => !prev);
    }
    if (onItemClick) {
      onItemClick(item);
    }
    if (item.handler) {
      item.handler();
    }
  }, [hasChildren, item, onItemClick]);

  // Handle keyboard shortcuts
  useHotkeys(
    item.hotkeys || "",
    (event) => {
      if (!item.hotkeys || item.hotkeys.trim() === "") return;
      event.preventDefault();
      if (item.handler) {
        item.handler();
      } else if (item.linkTo && !hasChildren) {
        window.location.href = item.linkTo;
      }
    },
    {
      scopes: HOT_KEYS_SIDEBAR,
      enableOnFormTags: ["INPUT", "TEXTAREA", "SELECT"],
    },
  );

  if (item.hidden) return null;

  const isSearchItem = item.id === "searchItem";

  // Generic badge: items can supply a useBadgeCount hook to show reactive counts.
  // Enterprise plugins register items with useBadgeCount (e.g. for human task inbox).
  // We call item.useBadgeCount if present, otherwise fall back to a no-op hook.
  const badgeCount = (item.useBadgeCount ?? (() => 0))();
  const showBadge = badgeCount > 0;

  // Check if link should open in new tab (isOpenNewTab flag or absolute URL)
  const isExternalLink =
    item.isOpenNewTab ||
    (item.linkTo &&
      (item.linkTo.startsWith("//") ||
        item.linkTo.startsWith("http://") ||
        item.linkTo.startsWith("https://")));

  const itemContent = (
    <ListItemButton
      id={item.id}
      component={
        item.linkTo && !hasChildren && item.linkTo !== "" && !isExternalLink
          ? Link
          : isExternalLink && item.linkTo && !hasChildren && item.linkTo !== ""
            ? "a"
            : "div"
      }
      to={
        item.linkTo && !hasChildren && item.linkTo !== "" && !isExternalLink
          ? item.linkTo
          : undefined
      }
      href={
        isExternalLink && item.linkTo && !hasChildren && item.linkTo !== ""
          ? item.linkTo
          : undefined
      }
      target={isExternalLink ? "_blank" : undefined}
      rel={isExternalLink ? "noopener noreferrer" : undefined}
      onClick={handleClick}
      onMouseEnter={
        !open && hasChildren && level === 0 ? onMouseEnter : undefined
      }
      onMouseLeave={
        !open && hasChildren && level === 0 ? onMouseLeave : undefined
      }
      sx={{
        minHeight: level > 0 ? 32 : isSearchItem ? 24 : 32,
        borderRadius: isSearchItem ? 8 : 1,
        mx: open ? (isSearchItem ? 1.5 : 1) : 0.5,
        mb: level > 0 ? 0.5 : isSearchItem ? 1.5 : 0.75,
        mt: isSearchItem ? 1.5 : 0,
        px: open ? (level > 0 ? 1.25 : isSearchItem ? 2 : 1.5) : 1,
        py: level > 0 ? 0.75 : isSearchItem ? 1.25 : 1,
        justifyContent: open ? "flex-start" : "center",
        position: "relative",
        transition: "all 0.2s ease-in-out",
        backgroundColor: isSearchItem
          ? alpha(theme.palette.action.hover, 0.05)
          : isParentWithActiveChild
            ? alpha(theme.palette.action.hover, 0.05)
            : active
              ? alpha(theme.palette.primary.main, 0.1)
              : "transparent",
        color: isSearchItem
          ? alpha(theme.palette.text.primary, 0.8)
          : isParentWithActiveChild
            ? theme.palette.text.primary
            : active
              ? theme.palette.primary.main
              : alpha(theme.palette.text.primary, 0.7),
        boxShadow: isSearchItem
          ? `0 1px 2px ${alpha(theme.palette.common.black, 0.05)}`
          : "none",
        "&:hover": {
          backgroundColor: isSearchItem
            ? alpha(theme.palette.action.hover, 0.08)
            : isParentWithActiveChild
              ? alpha(theme.palette.action.hover, 0.08)
              : active
                ? alpha(theme.palette.primary.main, 0.15)
                : alpha(theme.palette.action.hover, 0.05),
          borderColor: isSearchItem
            ? alpha(theme.palette.divider, 0.5)
            : "transparent",
          boxShadow: isSearchItem
            ? `0 2px 4px ${alpha(theme.palette.common.black, 0.08)}`
            : "none",
          color: isSearchItem
            ? theme.palette.text.primary
            : isParentWithActiveChild
              ? theme.palette.text.primary
              : active
                ? theme.palette.primary.main
                : theme.palette.text.primary,
        },
        ...(level > 0 &&
          open && {
            ml: 2,
            pl: 2,
            fontSize: "0.8125rem",
          }),
      }}
    >
      {item.icon && (
        <ListItemIcon
          sx={{
            minWidth: open ? (level > 0 ? 28 : isSearchItem ? 40 : 28) : "auto",
            justifyContent: open ? "flex-start" : "center",
            color: "inherit",
            marginRight: open && level === 0 && !isSearchItem ? 0.5 : 0,
            "& svg": {
              fontSize: level > 0 ? 16 : isSearchItem ? 22 : 20,
            },
          }}
        >
          {item.icon}
        </ListItemIcon>
      )}
      {open && (
        <>
          <ListItemText
            primary={
              showBadge ? (
                <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
                  <span>{item.title}</span>
                  <Box
                    sx={{
                      fontSize: "0.75rem",
                      height: "18px",
                      minWidth: "18px",
                      borderRadius: "9px",
                      backgroundColor: colors.red06,
                      color: "white",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      px: 0.5,
                    }}
                  >
                    {badgeCount}
                  </Box>
                </Box>
              ) : (
                item.title
              )
            }
            primaryTypographyProps={{
              fontSize:
                level > 0
                  ? "0.8125rem"
                  : isSearchItem
                    ? "0.9375rem"
                    : "0.9375rem",
              fontWeight: isSearchItem ? 600 : active ? 600 : 500,
              sx: {
                transition: "font-weight 0.2s ease-in-out",
                lineHeight: level > 0 ? 1.3 : 1.5,
              },
            }}
          />
          {item.shortcuts && item.shortcuts.length > 0 && (
            <HotKeysButton shortcuts={item.shortcuts} />
          )}
          {hasChildren && (
            <ChevronRightIcon
              sx={{
                fontSize: 16,
                transition: "transform 0.2s ease-in-out",
                transform: isExpanded ? "rotate(90deg)" : "rotate(0deg)",
                color: "inherit",
              }}
            />
          )}
        </>
      )}
    </ListItemButton>
  );

  // If item has a custom component, render it but still show children if it has them
  const hasCustomComponent =
    item.component && typeof item.component !== "function";
  const hasCustomComponentFunction =
    item.component && typeof item.component === "function";

  const isHovered = hoveredMenuId === item.id;
  const showPopper =
    !open &&
    hasChildren &&
    level === 0 &&
    isHovered &&
    !hasCustomComponent &&
    !hasCustomComponentFunction;

  return (
    <>
      {hasCustomComponentFunction && typeof item.component === "function" ? (
        <>
          {createElement(item.component, {
            isParent: Boolean(hasChildren),
            isTopParent: level === 0,
            isRouteActive: isRouteActive,
            isTopParentActive: level === 0 && isRouteActive,
            active: active,
            maybeActiveStyles: {},
            icon: item.icon,
          })}
        </>
      ) : hasCustomComponent ? (
        <Box>{item.component as ReactNode}</Box>
      ) : (
        <ListItem
          ref={itemRef as React.RefObject<HTMLLIElement>}
          disablePadding
          sx={{ display: "block", position: "relative" }}
        >
          {itemContent}
        </ListItem>
      )}
      {hasChildren && open && (
        <Collapse in={isExpanded} timeout="auto" unmountOnExit>
          <List
            component="div"
            disablePadding
            sx={{
              mt: 0.25,
              mb: 0.5,
              borderLeft: `1px solid ${alpha(theme.palette.divider, 0.5)}`,
              ml: 5,
              pl: 0.75,
            }}
          >
            {visibleChildren.map((child) => (
              <Box key={child.id} sx={{ mb: 0 }}>
                <SidebarItem
                  item={child}
                  level={level + 1}
                  open={open}
                  isActive={isRouteActive}
                />
              </Box>
            ))}
          </List>
        </Collapse>
      )}
      {showPopper && itemRef?.current && (
        <ClickAwayListener onClickAway={onMouseLeave || (() => {})}>
          <Popper
            open={isHovered}
            anchorEl={itemRef.current}
            placement="right-start"
            modifiers={[
              {
                name: "flip",
                options: {
                  enabled: true,
                  boundariesElement: "viewport",
                },
              },
            ]}
            sx={{ zIndex: 1501 }}
          >
            <Paper
              elevation={5}
              onMouseEnter={onPopoverMouseEnter}
              onMouseLeave={onPopoverMouseLeave}
              sx={{
                mt: 9,
                ml: 3.5,
                background: theme.palette.background.paper,
                color: theme.palette.text.primary,
                py: 1,
                minWidth: 200,
                borderRadius: 1,
                border: `1px solid ${alpha(theme.palette.divider, 0.1)}`,
              }}
            >
              <List component="nav" disablePadding>
                {visibleChildren.map((child) => (
                  <Box key={child.id}>
                    <SidebarItem
                      item={child}
                      level={1}
                      open={true}
                      isActive={isRouteActive}
                    />
                  </Box>
                ))}
              </List>
            </Paper>
          </Popper>
        </ClickAwayListener>
      )}
    </>
  );
};
