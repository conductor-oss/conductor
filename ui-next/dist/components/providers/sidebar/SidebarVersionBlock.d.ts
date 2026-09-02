interface SidebarVersionBlockProps {
    open: boolean;
    /**
     * undefined = API call still in-flight → show a skeleton placeholder.
     * null      = API settled without data (error / unavailable) → show just uiVersion.
     * string    = loaded successfully → show "conductorVersion | uiVersion".
     */
    conductorVersion?: string | null;
    uiVersion: string;
}
/**
 * Shared version block for the sidebar footer (logo, version copy, copyright).
 * Used by SidebarFooter and by SidebarMenu when rendering a custom userFooter.
 */
export declare function SidebarVersionBlock({ open, conductorVersion, uiVersion, }: SidebarVersionBlockProps): import("react").JSX.Element;
export {};
