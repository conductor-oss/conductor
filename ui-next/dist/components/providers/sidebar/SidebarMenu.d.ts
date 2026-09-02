import { ReactNode, RefObject } from "react";
import { Auth0User } from "types/User";
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
    isAuthenticated: boolean;
    isMobile: boolean;
    user: Auth0User | null;
    conductorUser: {
        id: string;
    } | null;
    logOut?: () => void;
    /** undefined = loading (skeleton), null = error/unavailable, string = loaded */
    conductorVersion?: string | null;
    uiVersion: string;
    showCopyAlert: boolean;
    setShowCopyAlert: (show: boolean) => void;
    /** When provided (e.g. by enterprise), used for the user block so auth comes from host app; version block still shown. */
    customUserBlock?: ReactNode;
}
export declare const SidebarMenu: ({ sections, open, hoveredMenuId, getItemRef, handleMouseEnter, handleMouseLeave, handlePopoverMouseEnter, handlePopoverMouseLeave, isAuthenticated, isMobile, user, conductorUser, logOut, conductorVersion, uiVersion, showCopyAlert, setShowCopyAlert, customUserBlock, }: SidebarMenuProps) => import("react").JSX.Element;
export {};
