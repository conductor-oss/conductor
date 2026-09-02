import { MenuItemType } from "./types";
interface SidebarProps {
    menuItems: MenuItemType[];
    open?: boolean;
    onToggle?: (open: boolean) => void;
    /** undefined = loading (skeleton), null = error/unavailable, string = loaded */
    apiVersion?: string | null;
    releaseVersion?: string;
    isAnnouncementBannerVisible?: boolean;
    customLogo?: string;
    isMobile?: boolean;
    toggleMenu?: () => void;
    onSearchClick?: () => void;
}
export declare const Sidebar: ({ menuItems, open: controlledOpen, onToggle, apiVersion, releaseVersion, isAnnouncementBannerVisible: _isAnnouncementBannerVisible, customLogo, isMobile, toggleMenu, onSearchClick, }: SidebarProps) => import("react").JSX.Element | null;
export default Sidebar;
