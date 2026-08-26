import { styled } from "@mui/material/styles";
import { ReactNode } from "react";
import { greyBorder, greyText, lightGrey } from "theme/tokens/colors";

// Metric, operator and value read as one compound control instead of two
// separate boxes each carrying their own floating label — the label moves
// into a single prefix chip, and the value is always enabled (no more
// grey "disabled" fields you have to unlock by picking a condition first).
const Shell = styled("div")({
  display: "flex",
  alignItems: "center",
  height: 32,
  width: "100%",
  border: `1px solid ${greyBorder}`,
  borderRadius: "4px",
  backgroundColor: "#fff",
  overflow: "hidden",
});

const Chip = styled("div")({
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
});

const ActiveDotMark = styled("span")(({ theme }) => ({
  width: 5,
  height: 5,
  borderRadius: "50%",
  backgroundColor: theme.palette.primary.main,
}));

const ActiveDot = ({ active }: { active: boolean }) =>
  active ? <ActiveDotMark data-testid="filter-active-dot" /> : null;

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
export const CompoundFilter = ({
  label,
  active,
  operator,
  value,
}: CompoundFilterProps) => (
  <Shell>
    <Chip>
      <ActiveDot active={active} />
      {label}
    </Chip>
    {operator}
    {value}
  </Shell>
);
