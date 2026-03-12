import {
  FormControl,
  InputLabel,
  Select as MuiSelect,
  SelectProps as MuiSelectProps,
} from "@mui/material";
import _isNil from "lodash/isNil";
import { CSSProperties, ReactNode } from "react";

interface SelectProps extends Omit<MuiSelectProps, "renderValue"> {
  label?: ReactNode;
  fullWidth?: boolean;
  nullable?: boolean;
  style?: CSSProperties;
  renderValue?: (value: unknown) => ReactNode;
}

const Select = ({
  label,
  fullWidth,
  nullable = true,
  style,
  ...props
}: SelectProps) => {
  return (
    <FormControl variant="outlined" fullWidth={fullWidth} style={style}>
      {label && <InputLabel>{label}</InputLabel>}
      <MuiSelect
        fullWidth={fullWidth}
        label={label}
        displayEmpty={nullable}
        renderValue={(v) => (_isNil(v) ? "" : (v as ReactNode))}
        {...props}
      />
    </FormControl>
  );
};

export default Select;
