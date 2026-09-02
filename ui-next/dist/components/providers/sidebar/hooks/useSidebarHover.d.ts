import { RefObject } from "react";
export declare const useSidebarHover: () => {
    hoveredMenuId: string | null;
    getItemRef: (itemId: string) => RefObject<HTMLElement | null>;
    handleMouseEnter: (itemId: string) => () => void;
    handleMouseLeave: () => void;
    handlePopoverMouseEnter: () => void;
    handlePopoverMouseLeave: () => void;
};
