import React from "react";
import { Input } from "./";
import Autocomplete from "@material-ui/lab/Autocomplete";
import FormControl from "@material-ui/core/FormControl";
import InputLabel from "@material-ui/core/InputLabel";
import CloseIcon from "@material-ui/icons/Close";
import { InputAdornment, CircularProgress } from "@material-ui/core";

export default function ({
  label,
  className,
  style,
  error,
  helperText,
  name,
  value,
  placeholder,
  loading,
  disabled,
  ...props
}) {
  return (
    <FormControl style={style} className={className}>
      {label && <InputLabel error={!!error}>{label}</InputLabel>}
      <Autocomplete
        {...props}
        disabled={loading || disabled}
        closeIcon={<CloseIcon />}
        renderInput={({ InputProps, ...params }) => (
          <Input
            {...params}
            InputProps={{
              ...InputProps,
              ...(loading && {
                startAdornment: (
                  <InputAdornment position="end">
                    <CircularProgress size={20} color="inherit" thickness={6} />
                  </InputAdornment>
                ),
              }),
            }}
            placeholder={loading ? "Loading Options" : placeholder}
            name={name}
            error={!!error}
            helperText={helperText}
          />
        )}
        value={value === undefined ? null : value} // convert undefined to null
      />
    </FormControl>
  );
}
