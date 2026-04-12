import { RefObject, useCallback, useEffect, useRef, useState } from "react";

export const useSidebarHover = () => {
  // Track which menu item is hovered (for showing sub items in popper when collapsed)
  const [hoveredMenuId, setHoveredMenuId] = useState<string | null>(null);

  // Timeout ref for delayed closing
  const closeTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Refs for menu items to anchor poppers
  const menuItemRefs = useRef<Record<string, RefObject<HTMLElement | null>>>(
    {},
  );

  const getItemRef = useCallback((itemId: string) => {
    if (!menuItemRefs.current[itemId]) {
      menuItemRefs.current[itemId] = { current: null };
    }
    return menuItemRefs.current[itemId];
  }, []);

  const handleMouseEnter = useCallback((itemId: string) => {
    return () => {
      // Clear any pending close timeout
      if (closeTimeoutRef.current) {
        clearTimeout(closeTimeoutRef.current);
        closeTimeoutRef.current = null;
      }
      setHoveredMenuId(itemId);
    };
  }, []);

  const handleMouseLeave = useCallback(() => {
    // Add a small delay before closing to allow mouse to move to popover
    if (closeTimeoutRef.current) {
      clearTimeout(closeTimeoutRef.current);
    }
    closeTimeoutRef.current = setTimeout(() => {
      setHoveredMenuId(null);
      closeTimeoutRef.current = null;
    }, 100);
  }, []);

  const handlePopoverMouseEnter = useCallback(() => {
    // Clear close timeout when mouse enters popover
    if (closeTimeoutRef.current) {
      clearTimeout(closeTimeoutRef.current);
      closeTimeoutRef.current = null;
    }
  }, []);

  const handlePopoverMouseLeave = useCallback(() => {
    // Close immediately when leaving popover
    if (closeTimeoutRef.current) {
      clearTimeout(closeTimeoutRef.current);
      closeTimeoutRef.current = null;
    }
    setHoveredMenuId(null);
  }, []);

  // Cleanup timeout on unmount
  useEffect(() => {
    return () => {
      if (closeTimeoutRef.current) {
        clearTimeout(closeTimeoutRef.current);
      }
    };
  }, []);

  return {
    hoveredMenuId,
    getItemRef,
    handleMouseEnter,
    handleMouseLeave,
    handlePopoverMouseEnter,
    handlePopoverMouseLeave,
  };
};
