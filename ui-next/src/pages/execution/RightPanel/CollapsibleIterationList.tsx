import {
  Box,
  Collapse,
  InputAdornment,
  OutlinedInput,
  Typography,
} from "@mui/material";
import { CaretDown, CaretRight, X } from "@phosphor-icons/react";
import React, {
  ReactNode,
  useCallback,
  useMemo,
  useRef,
  useState,
} from "react";
import { useVirtualizer } from "@tanstack/react-virtual";
import { colors } from "theme/tokens/variables";
import { buildSuggestions } from "./iterationHelpers";

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
  selectedLabel,
  renderItem,
  onSelect,
  isItemSelected,
  trailing,
  totalItems,
  onJumpTo,
  onScrollEnd,
  getItemValue,
}: CollapsibleIterationListProps<T>) {
  const [expanded, setExpanded] = useState(true);
  const showAll = true;
  const [query, setQuery] = useState("");
  const scrollElRef = useRef<HTMLDivElement | null>(null);

  const virtualizer = useVirtualizer({
    count: expanded && showAll && !query ? items.length : 0,
    getScrollElement: () => scrollElRef.current,
    estimateSize: () => ITEM_HEIGHT,
    overscan: 8,
  });

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

  const selectedIndex = items.findIndex(
    (item, i) => isItemSelected?.(item, i) ?? false,
  );
  const selectedItem = selectedIndex >= 0 ? items[selectedIndex] : undefined;

  const loadedMatches = useMemo(() => {
    if (!query) return [];
    return items.filter((item) => String(getItemValue(item)).startsWith(query));
  }, [items, query, getItemValue]);

  const jumpHints = useMemo((): number[] => {
    if (!query || !totalItems) return [];
    const num = parseInt(query, 10);
    if (isNaN(num) || num < 1 || num > totalItems) return [];
    const alreadyLoaded = items.some((item) => getItemValue(item) === num);
    if (alreadyLoaded) return [];
    return buildSuggestions(query, totalItems);
  }, [query, totalItems, items, getItemValue]);

  const listHeight = Math.min(items.length * ITEM_HEIGHT, MAX_LIST_HEIGHT);
  const isSearching = !!query;

  return (
    <Box
      sx={{
        width: "100%",
        border: "1px solid",
        borderColor: "divider",
        borderRadius: 1,
        overflow: "hidden",
        backgroundColor: "background.paper",
      }}
    >
      {/* ── Accordion header ── */}
      <Box
        onClick={() => setExpanded((v) => !v)}
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          px: 1.5,
          py: 0.75,
          cursor: "pointer",
          userSelect: "none",
          borderBottom: expanded ? "1px solid" : "none",
          borderColor: "divider",
          "&:hover": { backgroundColor: "action.hover" },
        }}
      >
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 2,
            width: "100%",
          }}
        >
          <Typography
            sx={{ fontSize: 14, fontWeight: 500, color: "text.primary" }}
          >
            {headerLabel}
          </Typography>
          {trailing}
        </Box>
        <Box
          sx={{ color: colors.gray04, display: "flex", alignItems: "center" }}
        >
          {expanded ? <CaretDown size={14} /> : <CaretRight size={14} />}
        </Box>
      </Box>

      <Collapse in={expanded} unmountOnExit>
        <Box
          sx={{
            overflow: "hidden",
            width: "100%",
            // pt: 0.5,
            // pb: 1,
            p: 2,
          }}
        >
          {/* ── Selected ── */}
          {(selectedItem || selectedLabel) && (
            <Box marginBottom={3}>
              <SectionLabel>Selected</SectionLabel>
              <Box sx={{ mx: 1, borderRadius: 1, overflow: "hidden" }}>
                <IterationRow
                  onClick={() => {
                    if (selectedItem) onSelect(selectedItem, selectedIndex);
                  }}
                  selected
                  clickable={!!selectedItem}
                >
                  {selectedItem
                    ? renderItem(selectedItem, selectedIndex)
                    : selectedLabel}
                </IterationRow>
              </Box>
            </Box>
          )}

          {/* ── Full list (search + browse) ── */}
          {showAll && (
            <>
              {/* Search input */}
              <Box sx={{ px: 1.5, py: 1 }}>
                <OutlinedInput
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Search or jump to iteration"
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

              {/* Browse mode */}
              {!isSearching && (
                <>
                  <SectionLabel>All Iterations</SectionLabel>
                  <Box
                    ref={(node: HTMLDivElement | null) => {
                      scrollElRef.current = node;
                    }}
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
                        const item = items[vItem.index];
                        const selected =
                          isItemSelected?.(item, vItem.index) ?? false;
                        return (
                          <IterationRow
                            key={vItem.index}
                            onClick={() => onSelect(item, vItem.index)}
                            selected={selected}
                            clickable
                            absolute
                            top={vItem.start}
                            height={vItem.size}
                          >
                            {renderItem(item, vItem.index)}
                          </IterationRow>
                        );
                      })}
                    </div>
                  </Box>
                </>
              )}

              {/* Search results */}
              {isSearching && (
                <>
                  <SectionLabel>Search Results</SectionLabel>
                  {loadedMatches.length === 0 && jumpHints.length === 0 && (
                    <Box
                      sx={{
                        px: 1.5,
                        py: 1.5,
                        fontSize: 14,
                        color: "text.secondary",
                      }}
                    >
                      No iterations found
                    </Box>
                  )}
                  {loadedMatches.map((item, i) => {
                    const realIndex = items.indexOf(item);
                    const idx = realIndex >= 0 ? realIndex : i;
                    const selected = isItemSelected?.(item, idx) ?? false;
                    return (
                      <IterationRow
                        key={i}
                        onClick={() => {
                          onSelect(item, idx);
                          setQuery("");
                        }}
                        selected={selected}
                        clickable
                      >
                        {renderItem(item, idx)}
                      </IterationRow>
                    );
                  })}
                  {jumpHints.map((num) => (
                    <Box
                      key={num}
                      onClick={() => {
                        onJumpTo?.(num);
                        setQuery("");
                      }}
                      sx={{
                        display: "flex",
                        alignItems: "center",
                        px: 1.5,
                        height: ITEM_HEIGHT,
                        cursor: "pointer",
                        fontSize: 14,
                        color: "primary.main",
                        "&:hover": { backgroundColor: "action.hover" },
                      }}
                    >
                      Iteration {num}
                    </Box>
                  ))}
                </>
              )}
            </>
          )}
        </Box>
      </Collapse>
    </Box>
  );
}

function IterationRow({
  children,
  onClick,
  selected,
  clickable,
  absolute,
  top,
  height,
}: {
  children: ReactNode;
  onClick?: () => void;
  selected?: boolean;
  clickable?: boolean;
  absolute?: boolean;
  top?: number;
  height?: number;
}) {
  return (
    <Box
      onClick={onClick}
      sx={{
        display: "flex",
        alignItems: "center",
        px: 1.5,
        height: height ?? ITEM_HEIGHT,
        fontSize: 13,
        fontWeight: selected ? 500 : 400,
        color: "text.primary",
        cursor: clickable ? "pointer" : "default",
        backgroundColor: selected ? "action.selected" : "transparent",
        borderRadius: selected ? 1 : 0,
        "&:hover": clickable
          ? {
              backgroundColor: selected ? "action.selected" : "action.hover",
              borderRadius: 1,
              opacity: selected ? 0.85 : 1,
            }
          : {},
        ...(absolute
          ? {
              position: "absolute",
              top: 0,
              width: "100%",
              transform: `translateY(${top}px)`,
            }
          : {}),
      }}
    >
      {children}
    </Box>
  );
}

function SectionLabel({ children, sx }: { children: ReactNode; sx?: object }) {
  return (
    <Typography
      sx={{
        px: 1.5,
        pt: 0.75,
        pb: 0.25,
        fontSize: 11,
        fontWeight: 600,
        color: colors.gray04,
        userSelect: "none",
        ...sx,
      }}
    >
      {children}
    </Typography>
  );
}
