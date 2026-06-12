import { Box, FormControl, MenuItem, useMediaQuery } from "@mui/material";
import { FunctionComponent, useMemo } from "react";

import MuiCheckbox from "components/ui/MuiCheckbox";
import StatusBadge from "components/StatusBadge";
import { ConductorSelect } from "components/ui/inputs";
import { Entries } from "types";
import { SelectableStatus } from "./state";

interface StatusSelectProps {
  onSelect: (selection?: SelectableStatus[]) => void;
  value: any[];
  summary?: Record<SelectableStatus, number>;
}

const ALL = "ALL";
const label = "Status Filter";

export const StatusSelect: FunctionComponent<StatusSelectProps> = ({
  onSelect,
  value,
  summary = {},
}) => {
  const isSmallWidth = useMediaQuery((theme: any) =>
    theme.breakpoints.down("sm"),
  );

  const options = useMemo(
    () =>
      (Object.entries(summary) as Entries<Record<SelectableStatus, number>>)
        .map(
          ([statusId, amount]): {
            statusId: SelectableStatus;
            amount: number;
          } => ({
            statusId,
            amount,
          }),
        )
        .sort((a, b) => a.statusId.localeCompare(b.statusId)),
    [summary],
  );

  const handleSelection = (event: any) => {
    const eventValue = event.target.value;
    onSelect(eventValue === ALL ? undefined : eventValue);
  };

  return (
    <FormControl variant="outlined" sx={{ m: 2, ml: 4 }}>
      <Box
        sx={{
          display: isSmallWidth ? "initial" : "flex",
          alignItems: "center",
        }}
      >
        <ConductorSelect
          onChange={handleSelection}
          fullWidth
          label={label}
          value={value ?? []}
          SelectProps={{
            multiple: true,
            displayEmpty: true,
            renderValue: (selected) =>
              Array.isArray(selected) && selected.length > 0
                ? selected.join(", ")
                : ALL,
          }}
          sx={{ minWidth: "160px" }}
        >
          {options.map(({ statusId, amount }) => (
            <MenuItem key={statusId} value={statusId}>
              <MuiCheckbox
                checked={value?.findIndex((item) => item === statusId) >= 0}
              />
              <StatusBadge status={statusId} labelConcat={` (${amount})`} />
            </MenuItem>
          ))}
        </ConductorSelect>
      </Box>
    </FormControl>
  );
};
