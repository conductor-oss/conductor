import {
  Box,
  InputAdornment,
  OutlinedInput,
  Popover,
  Typography,
} from "@mui/material";
import { CaretUpDown, X } from "@phosphor-icons/react";
import React, {
  ReactNode,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  useReducer,
} from "react";
import { useVirtualizer } from "@tanstack/react-virtual";
import { colors } from "theme/tokens/variables";

const ITEM_HEIGHT = 36;
const MAX_LIST_HEIGHT = 300;

export interface CollapsibleIterationListProps<T> {
  items: T[];
  headerLabel: ReactNode;
  selectedLabel?: string;
  renderItem: (item: T, index: number) => ReactNode;
  onSelect: (item: T, index: number) => void;
  isItemSelected?: (item: T, index: number) => boolean;
  trailing?: ReactNode;
  totalItems?: number;
  onPrefetch?: (value: number) => void;
  onJumpTo?: (value: number) => void;
  onScrollEnd?: () => void;
  getItemValue: (item: T) => number;
}

export function CollapsibleIterationList<T>({
  items,
  headerLabel,
  renderItem,
  onSelect,
  isItemSelected,
  trailing,
  onScrollEnd,
  getItemValue,
}: CollapsibleIterationListProps<T>) {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [query, setQuery] = useState("");
  const open = Boolean(anchorEl);
  const scrollElRef = useRef<HTMLDivElement | null>(null);
  // Forces a re-render once the Popover mounts the scroll container so the
  // virtualizer can measure it on first open (before any query change occurs).
  const [, forceUpdate] = useReducer((n: number) => n + 1, 0);
  const setScrollRef = useCallback((node: HTMLDivElement | null) => {
    scrollElRef.current = node;
    if (node) forceUpdate();
  }, []);

  // Pair each item with its original index so filtering doesn't lose position
  const filteredItems = useMemo(() => {
    const indexed = items.map((item, i) => ({ item, index: i }));
    if (!query) return indexed;
    return indexed.filter(({ item }) =>
      String(getItemValue(item)).startsWith(query),
    );
  }, [items, query, getItemValue]);

  const listHeight = useMemo(
    () => Math.min(filteredItems.length * ITEM_HEIGHT, MAX_LIST_HEIGHT),
    [filteredItems.length],
  );

  const virtualizer = useVirtualizer({
    count: filteredItems.length,
    getScrollElement: () => scrollElRef.current,
    estimateSize: () => ITEM_HEIGHT,
    overscan: 8,
  });

  // Scroll to top whenever the filtered set changes
  useEffect(() => {
    if (open) scrollElRef.current?.scrollTo({ top: 0 });
  }, [query, open]);

  const handleClose = useCallback(() => {
    setAnchorEl(null);
    setQuery("");
  }, []);

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      if (!onScrollEnd) return;
      const el = e.currentTarget;
      if (el.scrollHeight - el.scrollTop - el.clientHeight < 200) {
        onScrollEnd();
      }
    },
    [onScrollEnd],
  );

  const handleSelect = useCallback(
    (item: T, index: number) => {
      onSelect(item, index);
      handleClose();
    },
    [onSelect, handleClose],
  );

  return (
    <Box sx={{ display: "flex", alignItems: "center", gap: 3, width: "100%" }}>
      {/* Trigger — styled to match MUI Select (small) */}
      <Box
        onClick={(e) => setAnchorEl(e.currentTarget)}
        sx={{
          flex: 1,
          minWidth: 0,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          px: 1.5,
          height: 36,
          border: "1px solid",
          borderColor: open ? "primary.main" : "divider",
          borderRadius: 1,
          cursor: "pointer",
          fontSize: 13,
          backgroundColor: "background.paper",
          boxSizing: "border-box",
          "&:hover": { borderColor: "text.primary" },
        }}
      >
        <Box
          sx={{ flex: 1, minWidth: 0, display: "flex", alignItems: "center" }}
        >
          {headerLabel}
        </Box>
        <CaretUpDown size={14} color={colors.gray04} />
      </Box>

      {trailing && (
        <Box sx={{ display: "flex", alignItems: "center", flexShrink: 0 }}>
          {trailing}
        </Box>
      )}

      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
        transformOrigin={{ vertical: "top", horizontal: "left" }}
        disableAutoFocus
        PaperProps={{
          sx: {
            width: anchorEl?.offsetWidth,
            boxShadow: 3,
            borderRadius: 1,
            overflow: "hidden",
          },
        }}
      >
        {/* Search input */}
        <Box sx={{ px: 1, pt: 1, pb: 0.5 }}>
          <OutlinedInput
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search iterations…"
            fullWidth
            autoFocus
            size="small"
            sx={{ fontSize: 13 }}
            endAdornment={
              query ? (
                <InputAdornment position="end">
                  <Box
                    onClick={() => setQuery("")}
                    sx={{
                      display: "flex",
                      alignItems: "center",
                      color: colors.gray04,
                      cursor: "pointer",
                      "&:hover": { color: "text.primary" },
                    }}
                  >
                    <X size={13} />
                  </Box>
                </InputAdornment>
              ) : null
            }
          />
        </Box>

        {/* Virtualized list */}
        {filteredItems.length === 0 ? (
          <Typography
            sx={{ px: 1.5, py: 1.5, fontSize: 13, color: "text.secondary" }}
          >
            No iterations found
          </Typography>
        ) : (
          <Box
            ref={setScrollRef}
            onScroll={handleScroll}
            sx={{ height: listHeight, overflowY: "auto" }}
          >
            <div
              style={{
                height: virtualizer.getTotalSize(),
                width: "100%",
                position: "relative",
              }}
            >
              {virtualizer.getVirtualItems().map((vItem) => {
                const { item, index } = filteredItems[vItem.index];
                const selected = isItemSelected?.(item, index) ?? false;
                return (
                  <Box
                    key={getItemValue(item)}
                    onClick={() => handleSelect(item, index)}
                    sx={{
                      position: "absolute",
                      top: 0,
                      width: "100%",
                      transform: `translateY(${vItem.start}px)`,
                      height: vItem.size,
                      display: "flex",
                      alignItems: "center",
                      px: 2,
                      fontSize: 13,
                      cursor: "pointer",
                      fontWeight: selected ? 500 : 400,
                      backgroundColor: selected
                        ? "action.selected"
                        : "transparent",
                      "&:hover": {
                        backgroundColor: selected
                          ? "action.selected"
                          : "action.hover",
                        opacity: selected ? 0.85 : 1,
                      },
                    }}
                  >
                    {renderItem(item, index)}
                  </Box>
                );
              })}
            </div>
          </Box>
        )}
      </Popover>
    </Box>
  );
}
