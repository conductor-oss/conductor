import CloseIcon from "@mui/icons-material/Close";
import { Box, ButtonBase, IconButton } from "@mui/material";
import { alpha } from "@mui/material/styles";
import { useRef, useState } from "react";
import { PanelActions, ResponsivePanel } from "./ResponsivePanel";
import { MONO_FONT } from "./searchScopes";

/** One applied filter shown as a chip, e.g. "Status is any of Failed, Paused". */
export interface FilterChip {
  id: string;
  /** Field name, e.g. "Correlation id" or "Status". */
  label: string;
  values: string[];
  /** "contains" for word matches; otherwise "is" / "is any of". */
  matchesWords?: boolean;
  formatValue?: (value: string) => string;
  /** For ids and keys, which read better in a fixed-width font. */
  monospace?: boolean;
  onRemoveValue: (value: string) => void;
  onRemove: () => void;
}

const chipLabel = ({ label, values, matchesWords }: FilterChip) => {
  if (matchesWords) {
    return `${label} contains`;
  }
  return values.length > 1 ? `${label} is any of` : `${label} is`;
};

interface SearchChipProps {
  chip: FilterChip;
  open: boolean;
  onOpen: () => void;
  onClose: () => void;
}

const SearchChip = ({ chip, open, onOpen, onClose }: SearchChipProps) => {
  const anchorRef = useRef<HTMLButtonElement>(null);
  const { label, values, onRemove, onRemoveValue } = chip;
  const format = chip.formatValue ?? ((value: string) => value);
  const valueFont = chip.monospace
    ? { fontFamily: MONO_FONT, fontSize: 12 }
    : { fontSize: 13 };
  const multi = values.length > 1;

  return (
    <>
      <Box
        sx={{
          display: "inline-flex",
          alignItems: "center",
          maxWidth: "100%",
          height: { xs: 36, sm: 28 },
          borderRadius: 18,
          bgcolor: (theme) => alpha(theme.palette.primary.main, 0.1),
          color: "primary.dark",
          fontSize: 13,
        }}
      >
        <ButtonBase
          ref={anchorRef}
          aria-haspopup="dialog"
          aria-expanded={open}
          onClick={open ? onClose : onOpen}
          sx={{
            gap: 0.75,
            height: "100%",
            minWidth: 0,
            pl: 1.25,
            pr: 0.25,
            borderRadius: "18px 0 0 18px",
            fontSize: 13,
          }}
        >
          <Box component="span" sx={{ whiteSpace: "nowrap", opacity: 0.85 }}>
            <Box
              component="span"
              sx={{ display: { xs: "none", sm: "inline" } }}
            >
              {chipLabel(chip)}
            </Box>
            <Box
              component="span"
              sx={{ display: { xs: "inline", sm: "none" } }}
            >
              {multi ? label : `${label}:`}
            </Box>
          </Box>
          <Box
            component="span"
            sx={{
              display: { xs: multi ? "none" : "block", sm: "block" },
              minWidth: 0,
              maxWidth: { xs: 150, sm: 280 },
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
              ...valueFont,
              fontWeight: 600,
            }}
          >
            {values.map(format).join(chip.matchesWords ? " " : ", ")}
          </Box>
          {multi && (
            <Box
              component="span"
              sx={{ display: { xs: "inline", sm: "none" }, fontWeight: 600 }}
            >
              · {values.length}
            </Box>
          )}
        </ButtonBase>
        <IconButton
          size="small"
          onClick={onRemove}
          aria-label={`Remove ${label} filter`}
          sx={{
            width: { xs: 36, sm: 24 },
            height: { xs: 36, sm: 24 },
            mr: 0.25,
            color: "inherit",
          }}
        >
          <CloseIcon sx={{ fontSize: 14 }} />
        </IconButton>
      </Box>
      <ResponsivePanel
        anchorEl={anchorRef.current}
        open={open}
        onClose={onClose}
        title={multi ? `${label} · ${values.length} values` : `${label} filter`}
      >
        <Box
          component="ul"
          sx={{
            m: 0,
            px: 0.5,
            py: 1,
            listStyle: "none",
            maxHeight: 260,
            overflowY: "auto",
          }}
        >
          {values.map((value) => (
            <Box
              component="li"
              key={value}
              sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                gap: 1,
                minHeight: { xs: 44, sm: 40 },
                pl: 1.25,
                pr: 0.5,
                borderRadius: 1.5,
                "&:hover": { bgcolor: "action.hover" },
              }}
            >
              <Box
                component="span"
                sx={{
                  minWidth: 0,
                  ...valueFont,
                  overflowWrap: "anywhere",
                }}
              >
                {format(value)}
              </Box>
              <IconButton
                size="small"
                onClick={() => onRemoveValue(value)}
                aria-label={`Remove ${format(value)}`}
                sx={{ width: 36, height: 36, flexShrink: 0 }}
              >
                <CloseIcon sx={{ fontSize: 14 }} />
              </IconButton>
            </Box>
          ))}
        </Box>
        <PanelActions
          onClear={onRemove}
          clearLabel="Remove filter"
          onCancel={onClose}
          onApply={onClose}
          applyLabel="Done"
        />
      </ResponsivePanel>
    </>
  );
};

export interface SearchChipsProps {
  chips: FilterChip[];
}

/**
 * Values added from the search bar, as chips. On phones a chip with several
 * values shows a count instead, so the row stays short. Clicking a chip lists
 * its values so each can be removed.
 */
export const SearchChips = ({ chips }: SearchChipsProps) => {
  const [openId, setOpenId] = useState<string | null>(null);

  if (chips.length === 0) {
    return null;
  }

  return (
    <Box
      sx={{
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: 0.75,
      }}
    >
      {chips.map((chip) => (
        <SearchChip
          key={chip.id}
          chip={{
            ...chip,
            onRemoveValue: (value) => {
              if (chip.values.length === 1) setOpenId(null);
              chip.onRemoveValue(value);
            },
            onRemove: () => {
              setOpenId(null);
              chip.onRemove();
            },
          }}
          open={openId === chip.id}
          onOpen={() => setOpenId(chip.id)}
          onClose={() => setOpenId(null)}
        />
      ))}
    </Box>
  );
};
