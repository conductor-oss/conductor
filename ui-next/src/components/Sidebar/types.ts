import { ReactNode, ComponentType } from "react";
import { CSSObject } from "@mui/material/styles";

export type MenuItemComponentType =
  | ReactNode
  | ComponentType<{
      isParent: boolean;
      isTopParent: boolean;
      isRouteActive: boolean;
      isTopParentActive: boolean;
      active: boolean;
      maybeActiveStyles: CSSObject;
      icon?: ReactNode;
    }>;

export interface MenuItemType {
  id: string;
  title: string;
  icon: ReactNode;
  linkTo?: string;
  activeRoutes?: string[];
  shortcuts: string[];
  hotkeys?: string;
  items?: MenuItemType[];
  isSmall?: boolean;
  component?: MenuItemComponentType;
  hidden: boolean;
  isOpenNewTab?: boolean;
  textStyle?: CSSObject;
  buttonContainerStyle?: CSSObject;
  iconContainerStyles?: CSSObject;
  handler?: () => void;
  /**
   * Optional numeric position for ordering (e.g. 10, 20, 30). Gaps allow plugins to
   * inject items in between (e.g. position 15 between 10 and 20).
   */
  position?: number;
  /**
   * Optional React hook that returns the current badge count for this item.
   * When the returned value is > 0, a red badge with the count is shown next
   * to the item title. Enterprise plugins use this to show pending task counts.
   *
   * Must follow React hook rules (called unconditionally in component render).
   * Default: returns 0 (no badge).
   */
  useBadgeCount?: () => number;
}

export interface SubMenuProps extends MenuItemType {
  items: MenuItemType[];
  parentId?: string;
}
