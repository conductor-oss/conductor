import SearchIcon from "@mui/icons-material/Search";
import { Box, Checkbox, FormControlLabel, InputBase } from "@mui/material";
import WorkflowStatusBadge from "components/WorkflowStatusBadge";
import { useMemo, useState } from "react";
import { WorkflowExecutionStatus } from "types/Execution";
import { PanelActions } from "./ResponsivePanel";
import { fieldBorderColor, helperTextColor } from "./styles";

const MAX_LISTED_NAMES = 100;

const optionRowSx = {
  display: "flex",
  alignItems: "center",
  m: 0,
  minHeight: { xs: 44, sm: 40 },
  px: 2,
  borderRadius: 1.5,
  "&:hover": { bgcolor: "action.hover" },
  "& .MuiFormControlLabel-label": { minWidth: 0, fontSize: 13 },
};

const toggle = (list: string[], value: string) =>
  list.includes(value) ? list.filter((v) => v !== value) : [...list, value];

const articleFor = (noun: string) => (/^[aeiou]/i.test(noun) ? "an" : "a");

const applyLabel = (count: number) => (count ? `Apply (${count})` : "Apply");

export interface NameFilterPanelProps {
  names: string[];
  /** What is being named, used in the find box and empty state: "workflow", "agent". */
  noun: string;
  selected: string[];
  onApply: (names: string[]) => void;
  onCancel: () => void;
}

/**
 * Pick names from the definitions list. A term containing `*` can be
 * added as a wildcard pattern, which the backend matches with LIKE.
 */
export const NameFilterPanel = ({
  names,
  noun,
  selected,
  onApply,
  onCancel,
}: NameFilterPanelProps) => {
  const [draft, setDraft] = useState<string[]>(selected);
  const [query, setQuery] = useState("");

  const sortedNames = useMemo(
    () =>
      [...names].sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase())),
    [names],
  );

  const trimmedQuery = query.trim();
  const isPattern = trimmedQuery.includes("*");
  const needle = trimmedQuery.replace(/\*/g, "").toLowerCase();
  const matches = sortedNames.filter((name) =>
    name.toLowerCase().includes(needle),
  );
  // Selected names that are not definitions (patterns, deleted workflows) stay
  // visible at the top so they can be unchecked.
  const extraSelected = draft.filter((name) => !sortedNames.includes(name));
  const listed = matches.slice(0, MAX_LISTED_NAMES);

  return (
    <>
      {/* Spacing is in 4px units: 20px from the panel edges, like the date picker. */}
      <Box sx={{ px: 5, pt: 5, pb: 3 }}>
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            gap: 1,
            height: { xs: 44, sm: 40 },
            px: 1.25,
            border: 1,
            borderColor: fieldBorderColor,
            borderRadius: 1.5,
            "&:focus-within": {
              borderColor: "primary.main",
              boxShadow: (theme) => `0 0 0 1px ${theme.palette.primary.main}`,
            },
          }}
        >
          <SearchIcon
            aria-hidden="true"
            sx={{ fontSize: 18, color: helperTextColor }}
          />
          <InputBase
            autoFocus
            fullWidth
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={`Find ${articleFor(noun)} ${noun}, or use * as a wildcard`}
            inputProps={{ "aria-label": `Find ${noun}` }}
            sx={{ fontSize: { xs: 16, sm: 13 } }}
          />
        </Box>
      </Box>
      <Box sx={{ maxHeight: 260, overflowY: "auto", px: 3, pb: 3 }}>
        {isPattern && !draft.includes(trimmedQuery) && (
          <FormControlLabel
            sx={optionRowSx}
            control={
              <Checkbox
                size="small"
                checked={false}
                onChange={() => setDraft([...draft, trimmedQuery])}
              />
            }
            label={`Match pattern “${trimmedQuery}”`}
          />
        )}
        {[...extraSelected, ...listed].map((name) => (
          <FormControlLabel
            key={name}
            sx={optionRowSx}
            control={
              <Checkbox
                size="small"
                checked={draft.includes(name)}
                onChange={() => setDraft(toggle(draft, name))}
              />
            }
            label={
              <Box
                component="span"
                sx={{
                  display: "block",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                }}
              >
                {name}
              </Box>
            }
          />
        ))}
        {matches.length > MAX_LISTED_NAMES && (
          <Box sx={{ px: 2, py: 2, fontSize: 12, color: helperTextColor }}>
            Showing the first {MAX_LISTED_NAMES} of {matches.length}. Type to
            narrow the list.
          </Box>
        )}
        {listed.length === 0 && extraSelected.length === 0 && !isPattern && (
          <Box sx={{ px: 2, py: 2, fontSize: 12, color: helperTextColor }}>
            No {noun}s match. Add * to search by pattern.
          </Box>
        )}
      </Box>
      <PanelActions
        onClear={() => setDraft([])}
        onCancel={onCancel}
        onApply={() => onApply(draft)}
        applyLabel={applyLabel(draft.length)}
      />
    </>
  );
};

const WORKFLOW_STATUSES = Object.values(WorkflowExecutionStatus);

export interface StatusFilterPanelProps {
  selected: string[];
  onApply: (statuses: string[]) => void;
  onCancel: () => void;
}

export const StatusFilterPanel = ({
  selected,
  onApply,
  onCancel,
}: StatusFilterPanelProps) => {
  const [draft, setDraft] = useState<string[]>(selected);

  return (
    <>
      <Box sx={{ px: 3, py: 3 }}>
        {WORKFLOW_STATUSES.map((status) => (
          <FormControlLabel
            key={status}
            sx={{ ...optionRowSx, width: "100%" }}
            control={
              <Checkbox
                size="small"
                checked={draft.includes(status)}
                onChange={() => setDraft(toggle(draft, status))}
                inputProps={{ "aria-label": status }}
              />
            }
            label={<WorkflowStatusBadge status={status} />}
          />
        ))}
      </Box>
      <PanelActions
        onClear={() => setDraft([])}
        onCancel={onCancel}
        onApply={() => onApply(draft)}
        applyLabel={applyLabel(draft.length)}
      />
    </>
  );
};
