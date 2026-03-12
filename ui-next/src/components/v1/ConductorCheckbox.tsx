import { FormControlLabel } from "@mui/material";
import MuiCheckbox from "components/MuiCheckbox";

export type ConductorCheckboxProps = {
  id?: string;
  label?: string;
  value?: boolean;
  onChange?: (value: boolean) => void;
};

export const ConductorCheckbox = ({
  id,
  label,
  value,
  onChange,
}: ConductorCheckboxProps) => {
  return (
    <FormControlLabel
      control={
        <MuiCheckbox
          id={id}
          checked={value}
          onChange={(__, value) => {
            onChange?.(value);
          }}
        />
      }
      label={label}
    />
  );
};
