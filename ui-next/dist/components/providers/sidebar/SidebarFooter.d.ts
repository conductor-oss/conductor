import { Auth0User } from "types/User";
import type { ReactNode } from "react";
interface SidebarFooterProps {
    open: boolean;
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
    /** When provided (e.g. by enterprise), used for user/sign-out block; version still shown below. */
    customUserBlock?: ReactNode;
}
export declare const SidebarFooter: ({ open, isAuthenticated, isMobile, user, conductorUser, logOut, conductorVersion, uiVersion, showCopyAlert, setShowCopyAlert, customUserBlock, }: SidebarFooterProps) => import("react").JSX.Element;
export {};
