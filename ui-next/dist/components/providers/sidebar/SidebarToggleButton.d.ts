interface SidebarToggleButtonProps {
    open: boolean;
    isMobile: boolean;
    topMargin: string;
    onToggle: () => void;
}
export declare const SidebarToggleButton: ({ open, isMobile, topMargin, onToggle, }: SidebarToggleButtonProps) => import("react").JSX.Element | null;
export {};
