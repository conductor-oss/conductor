import { Box } from "@mui/material";
import { FunctionComponent, ReactNode } from "react";
import { greyBorder, greyText, lightGrey } from "theme/tokens/colors";

// Metric, operator and value read as one compound control instead of two
// separate boxes each carrying their own floating label — the label moves
// into a single prefix chip, and the value is always enabled (no more
// grey "disabled" fields you have to unlock by picking a condition first).
const shellSx = {
  display: "flex",
  alignItems: "center",
  height: 32,
  width: "100%",
  border: `1px solid ${greyBorder}`,
  borderRadius: "4px",
  backgroundColor: "#fff",
  overflow: "hidden",
};

const chipSx = {
  display: "flex",
  alignItems: "center",
  gap: "6px",
  height: "100%",
  flexShrink: 0,
  padding: "0 10px",
  borderRight: `1px solid ${greyBorder}`,
  backgroundColor: lightGrey,
  fontSize: 12,
  fontWeight: 500,
  color: greyText,
  whiteSpace: "nowrap",
};

const ActiveDot: FunctionComponent<{ active: boolean }> = ({ active }) =>
  active ? (
    <Box
      data-testid="filter-active-dot"
      sx={{
        width: 5,
        height: 5,
        borderRadius: "50%",
        backgroundColor: "primary.main",
      }}
    />
  ) : null;

export interface CompoundFilterProps {
  label: string;
  active: boolean;
  operator: ReactNode;
  value: ReactNode;
}

// A single filter condition rendered as one bordered "metric | operator |
// value" control — label chip on the left (with a dot that lights up once
// the filter is actually active), then whatever operator/value controls
// the caller supplies. See queueMonitor/filter/FilterSection.tsx for the
// operator (OperatorSelect) and value fields this is normally paired with.
export const CompoundFilter: FunctionComponent<CompoundFilterProps> = ({
  label,
  active,
  operator,
  value,
}) => (
  <Box sx={shellSx}>
    <Box sx={chipSx}>
      <ActiveDot active={active} />
      {label}
    </Box>
    {operator}
    {value}
  </Box>
);
