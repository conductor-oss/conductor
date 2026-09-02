import React from "react";
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
export declare const SidebarItem: ({ item, level, open, isActive, onItemClick, hoveredMenuId, onMouseEnter, onMouseLeave, onPopoverMouseEnter, onPopoverMouseLeave, itemRef, }: SidebarItemProps) => React.JSX.Element | null;
export {};
