import { useSortable } from "@dnd-kit/sortable";
import { Box, Collapse, MenuItem, Theme, Tooltip } from "@mui/material";
import {
  CaretDown,
  CaretRight,
  DotsSixVertical,
  Trash,
} from "@phosphor-icons/react";
import { ChangeEvent, ReactNode, useEffect, useRef, useState } from "react";

import MuiTypography from "components/ui/MuiTypography";
import IconButton from "components/ui/buttons/MuiIconButton";
import ConductorSelect from "components/ui/inputs/ConductorSelect";
import { EventHandlerAction } from "types/Events";
import { actionMeta, actionOrder, actionSummary } from "./actionMeta";
import { Action } from "./state/types";

const FLASH_MS = 1600;

const cardStyle = {
  border: (theme: Theme) => `1px solid ${theme.palette.divider}`,
  borderRadius: 1,
  overflow: "hidden",
  transition: (theme: Theme) =>
    theme.transitions.create(["border-color", "box-shadow"]),
};

const flashedCardStyle = {
  ...cardStyle,
  borderColor: (theme: Theme) => theme.palette.primary.main,
  boxShadow: (theme: Theme) => `0 0 0 3px ${theme.palette.primary.main}33`,
};

// NB: this theme's spacing unit is 4px, not MUI's default 8px.
const headerStyle = {
  display: "flex",
  flexWrap: "wrap",
  alignItems: "center",
  gap: 2,
  p: 1.5,
  pl: 2,
  backgroundColor: (theme: Theme) => theme.palette.action.hover,
  borderBottom: (theme: Theme) => `1px solid ${theme.palette.divider}`,
};

const handleStyle = {
  display: "flex",
  color: "text.secondary",
  cursor: "grab",
  touchAction: "none",
  borderRadius: 0.5,
  "&:active": { cursor: "grabbing" },
  "&:focus-visible": {
    outline: (theme: Theme) => `2px solid ${theme.palette.primary.main}`,
  },
};

const summaryStyle = {
  flex: "1 1 80px",
  minWidth: 0,
  overflow: "hidden",
  textOverflow: "ellipsis",
  whiteSpace: "nowrap",
  fontFamily: "source-code-pro, Menlo, Monaco, Consolas, monospace",
  color: "text.secondary",
};

/**
 * Collapsible, sortable chrome around one action's form. The header keeps the
 * type and a one-line summary visible while the body is closed, so a handler
 * with several actions stays scannable. Only the grip handle starts a drag, so
 * the inputs inside the card keep their normal pointer behaviour.
 */
const ActionCard = ({
  id,
  index,
  payload,
  onRemove,
  onChangeType,
  highlightToken,
  children,
}: {
  /** Stable sortable id; survives reorders, unlike `index`. */
  id: string;
  index: number;
  payload: EventHandlerAction;
  onRemove: () => void;
  onChangeType: (next: Action) => void;
  /**
   * Bumped to a new value each time this card is the one just added. Null for
   * cards that were already there.
   */
  highlightToken?: number | null;
  children: ReactNode;
}) => {
  const [open, setOpen] = useState(true);
  const [flashing, setFlashing] = useState(false);
  const cardRef = useRef<HTMLDivElement | null>(null);
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id });

  // A new action lands at the top of a section that is itself the last thing
  // on the page, so it can render below the fold. Bring it into view and
  // flash it rather than leaving the user to guess whether the click worked.
  useEffect(() => {
    if (highlightToken == null) return;

    cardRef.current?.scrollIntoView?.({
      behavior: "smooth",
      block: "nearest",
    });
    setFlashing(true);
    const timer = setTimeout(() => setFlashing(false), FLASH_MS);
    return () => clearTimeout(timer);
  }, [highlightToken]);

  const type = payload.action as Action;
  const { icon: Icon } = actionMeta[type];
  const Chevron = open ? CaretDown : CaretRight;

  return (
    <Box
      ref={(node: HTMLDivElement | null) => {
        cardRef.current = node;
        setNodeRef(node);
      }}
      sx={flashing ? flashedCardStyle : cardStyle}
      style={{
        // Translate only: sortable items of different heights would otherwise
        // be scaled to match the slot they pass over.
        transform: transform
          ? `translate3d(${transform.x}px, ${transform.y}px, 0)`
          : undefined,
        transition,
        position: "relative",
        zIndex: isDragging ? 1 : undefined,
        opacity: isDragging ? 0.85 : undefined,
      }}
    >
      <Box sx={headerStyle}>
        <Box
          ref={setActivatorNodeRef}
          sx={handleStyle}
          {...attributes}
          {...listeners}
          aria-label="Reorder action"
        >
          <DotsSixVertical size={18} weight="bold" />
        </Box>
        <IconButton
          size="small"
          aria-label={open ? "Collapse action" : "Expand action"}
          onClick={() => setOpen(!open)}
        >
          <Chevron size={16} />
        </IconButton>
        <Box sx={{ display: "flex", color: "primary.main" }}>
          <Icon size={18} />
        </Box>
        <Box sx={{ flex: "0 1 190px", minWidth: 150 }}>
          <ConductorSelect
            fullWidth
            size="small"
            value={type ?? ""}
            inputProps={{ "aria-label": `Action ${index + 1} type` }}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              onChangeType(event.target.value as Action)
            }
          >
            {actionOrder.map((value) => (
              <MenuItem key={value} value={value}>
                {actionMeta[value].label}
              </MenuItem>
            ))}
          </ConductorSelect>
        </Box>
        <MuiTypography variant="caption" sx={summaryStyle}>
          {actionSummary(payload)}
        </MuiTypography>
        <Tooltip title="Remove action" arrow>
          <IconButton
            size="small"
            aria-label="Remove action"
            onClick={onRemove}
          >
            <Trash size={18} />
          </IconButton>
        </Tooltip>
      </Box>
      <Collapse in={open} unmountOnExit>
        {/*
          Top-heavy on purpose. The sub-forms add their own `my: 2`, and most
          of them open with an outlined input whose floating label overhangs
          the border by about half its height — so an even split leaves the
          first label visibly tighter to the header bar than the last field is
          to the card edge. The extra 8px at the top buys that back.
        */}
        <Box sx={{ px: 4, pt: 4, pb: 2 }}>{children}</Box>
      </Collapse>
    </Box>
  );
};

export default ActionCard;
