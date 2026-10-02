import { FormControlLabel, Switch } from "@mui/material";

export interface SearchModeSwitchProps {
  /** True when the page searches with a SQL query instead of the basic filters. */
  checked: boolean;
  onChange: (checked: boolean) => void;
}

/**
 * Toggles an execution search page between its basic filters and SQL query
 * mode. It has no outer spacing; the page or filter layout places it.
 */
export const SearchModeSwitch = ({
  checked,
  onChange,
}: SearchModeSwitchProps) => (
  <FormControlLabel
    sx={{ m: 0 }}
    checked={checked}
    control={
      <Switch
        color="primary"
        onChange={(event) => onChange(event.target.checked)}
      />
    }
    label="SQL query"
    slotProps={{ typography: { fontSize: 13 } }}
  />
);
