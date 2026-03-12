import Collapse from "@mui/material/Collapse";
import List from "@mui/material/List";
import { useContext, useMemo } from "react";
import { colors } from "theme/tokens/variables";
import { SidebarContext } from "./context/SidebarContext";
import { SidebarItem } from "./SidebarItem";
import { SubMenuProps } from "./types";

export const BaseSubMenu = ({ items, parentId }: SubMenuProps) => {
  const { open, openedMenus, location } = useContext(SidebarContext);
  const isSubMenuOpen = openedMenus?.some((menu) => menu === parentId);

  const treeStyle = useMemo(() => {
    const isActive = items.some((item) => item.linkTo === location?.pathname);
    if (open) {
      if (isActive) {
        return {
          height: "22px",
          top: "-4px",
        };
      }

      return {
        height: "32px",
        top: "-14px",
      };
    }

    return {
      height: "26px",
      top: "-8px",
    };
  }, [items, location?.pathname, open]);

  return (
    <Collapse
      in={isSubMenuOpen}
      timeout="auto"
      unmountOnExit
      sx={{
        color: colors.sidebarBlacky,
        width: "100%",
      }}
    >
      <List
        component="ul"
        disablePadding
        sx={{
          "& > .MuiListItem-root": {
            "&:first-of-type": {
              ".MuiButtonBase-root": {
                // mt: 1,
                ":before": treeStyle,
              },
            },

            ".MuiButtonBase-root": {
              color: colors.sidebarGreyText,
              py: 0,
              transition: " background-color 0.3s ease-in-out",

              ":before": {
                ml: 1,
                content: "''",
                borderLeft: `1px solid ${colors.sidebarFaintGrey}`,
                position: "absolute",
                width: "10px",
                height: "36px",
                top: "-20px",
                left: "15px",
              },

              ".MuiBox-root": {
                ml: 7,
              },

              ".MuiListItemText-root": {
                ".MuiListItemText-primary": {
                  fontSize: 12,
                },
              },
            },
          },
        }}
      >
        {items.map((item) => (
          <SidebarItem key={item.id} item={item} />
        ))}
      </List>
    </Collapse>
  );
};
