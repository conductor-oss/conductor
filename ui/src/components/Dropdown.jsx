import React from "react";
import { Input } from "./";
import Autocomplete from "@material-ui/lab/Autocomplete";
import FormControl from "@material-ui/core/FormControl";
import InputLabel from "@material-ui/core/InputLabel";

export default function ({
  label,
  className,
  style,
  error,
  helperText,
  ...props
}) {
  return (
    <FormControl style={style} className={className}>
      {label && <InputLabel error={!!error}>{label}</InputLabel>}
      <Autocomplete
        renderInput={(params) => (
          <Input {...params} error={!!error} helperText={helperText} />
        )}
        {...props}
      />
    </FormControl>
  );
}
