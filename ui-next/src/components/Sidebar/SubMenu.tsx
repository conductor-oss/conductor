import ClickAwayListener from "@mui/material/ClickAwayListener";
import ListItem from "@mui/material/ListItem";
import Popper from "@mui/material/Popper";
import Paper from "components/ui/Paper";
import { useCallback, useContext, useRef } from "react";
import { matchPath } from "react-router";
import { colors } from "theme/tokens/variables";
import { BaseSubMenu } from "./BaseSubMenu";
import { SidebarContext } from "./context/SidebarContext";
import { SidebarItem } from "./SidebarItem";
import { SubMenuProps } from "./types";

export const SubMenu = (props: SubMenuProps) => {
  const { open, openedMenus, setOpenedMenus, addMenu, location } =
    useContext(SidebarContext);
  const { id, items } = props;
  const itemRef = useRef<HTMLLIElement | null>(null);

  const handlePopperClose = useCallback(() => {
    setOpenedMenus([]);
  }, [setOpenedMenus]);

  const handlePopperOpen = useCallback(() => {
    addMenu(id);
  }, [id, addMenu]);

  const isPopperOpen = openedMenus.includes(id);
  const isActive = items.some(
    (item) =>
      item.linkTo === location?.pathname ||
      !!item.activeRoutes?.some((route) =>
        matchPath({ path: route, end: true }, location?.pathname || ""),
      ),
  );

  // Extract item from props (excluding items and parentId which are SubMenu-specific)
  const { items: _, parentId: _parentId, ...item } = props;

  return (
    <>
      <SidebarItem item={item} itemRef={itemRef} isActive={isActive} />
      {open ? (
        <ListItem
          sx={{
            p: 0,
          }}
        >
          <BaseSubMenu {...props} parentId={id} />
        </ListItem>
      ) : (
        <ClickAwayListener onClickAway={handlePopperClose}>
          <Popper
            id="mouse-over-popper"
            open={isPopperOpen}
            anchorEl={itemRef.current}
            modifiers={[
              {
                name: "flip",
                options: {
                  enabled: true,
                  boundariesElement: "viewport",
                },
              },
            ]}
            placement="right-start"
          >
            <Paper
              elevation={5}
              onMouseEnter={handlePopperOpen}
              onMouseLeave={handlePopperClose}
              sx={{
                mt: 9,
                ml: 3.5,
                background: colors.white,
                color: colors.sidebarBlacky,
                py: 1,
              }}
            >
              <BaseSubMenu {...props} parentId={id} />
            </Paper>
          </Popper>
        </ClickAwayListener>
      )}
    </>
  );
};

export default SubMenu;
