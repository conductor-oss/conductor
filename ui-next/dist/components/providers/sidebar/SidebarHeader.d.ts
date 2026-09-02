interface SidebarHeaderProps {
    open: boolean;
    isMobile: boolean;
    customLogo?: string;
    toggleMenu?: () => void;
    onSearchClick?: () => void;
}
export declare const SidebarHeader: ({ open, isMobile, customLogo, toggleMenu, onSearchClick, }: SidebarHeaderProps) => import("react").JSX.Element;
export {};
