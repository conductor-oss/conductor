import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import { Box, Checkbox, Menu, MenuItem } from "@mui/material";
import { Button } from "components";
import StatusBadge from "components/StatusBadge";
import { useState } from "react";
import { WorkflowExecutionStatus } from "types/Execution";

const workflowStatuses = Object.values(WorkflowExecutionStatus);

export interface StatusComboButtonProps {
  status: string[];
  /** Applied when the user clicks "Apply" in the menu. */
  onStatusChange: (val: string[]) => void;
  disabled?: boolean;
}

export function StatusComboButton({
  status,
  onStatusChange,
  disabled,
}: StatusComboButtonProps) {
  const [draft, setDraft] = useState<string[]>(status);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);

  const label =
    status.length === 0
      ? "Status"
      : status.length === 1
        ? status[0]
        : `Status (${status.length})`;

  return (
    <>
      <Button
        variant="outlined"
        size="small"
        disabled={disabled}
        endIcon={<ArrowDropDownIcon />}
        onClick={(e) => {
          setDraft(status);
          setAnchor(e.currentTarget);
        }}
        sx={{
          textTransform: "none",
          whiteSpace: "nowrap",
          height: 36,
          px: 1.5,
          minWidth: 120,
          flexShrink: 0,
          fontSize: "13px",
          fontWeight: 500,
          letterSpacing: "normal",
          borderColor:
            status.length > 0
              ? "primary.main"
              : (t) =>
                  t.palette.mode === "dark"
                    ? "rgba(255,255,255,0.23)"
                    : "rgba(0,0,0,0.23)",
          "&:hover": {
            borderColor: status.length > 0 ? "primary.dark" : "primary.main",
            backgroundColor: "transparent",
          },
          "&&": {
            color: status.length > 0 ? "primary.main" : "#060606",
          },
        }}
      >
        {label}
      </Button>

      <Menu
        anchorEl={anchor}
        open={Boolean(anchor)}
        onClose={() => setAnchor(null)}
        slotProps={{ paper: { sx: { minWidth: 160 } } }}
      >
        {workflowStatuses.map((s) => (
          <MenuItem
            key={s}
            dense
            onClick={() =>
              setDraft((prev) =>
                prev.includes(s) ? prev.filter((x) => x !== s) : [...prev, s],
              )
            }
            sx={{ px: 1.5 }}
          >
            <Checkbox
              checked={draft.includes(s)}
              size="small"
              sx={{ p: 0.5, mr: 0.5 }}
            />
            <StatusBadge status={s as any} />
          </MenuItem>
        ))}

        <Box
          sx={{
            display: "flex",
            justifyContent: "flex-end",
            gap: 1,
            px: 1.5,
            pt: 1.5,
            borderTop: (t) => `1px solid ${t.palette.divider}`,
          }}
        >
          <Button
            variant="text"
            size="small"
            sx={{ textTransform: "none" }}
            onClick={() => setAnchor(null)}
          >
            Cancel
          </Button>
          <Button
            variant="contained"
            size="small"
            sx={{ textTransform: "none" }}
            onClick={() => {
              onStatusChange(draft);
              setAnchor(null);
            }}
          >
            Apply
          </Button>
        </Box>
      </Menu>
    </>
  );
}
